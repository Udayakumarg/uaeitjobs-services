package com.uaeitjobs.service.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls UAE IT jobs from JSearch (RapidAPI), which wraps Google for Jobs
 * and indexes LinkedIn, Indeed, Bayt, GulfTalent, company career pages.
 *
 * Note this source does NOT implement JobIngestSource — it is driven by
 * the keyword rotation in KeywordIngestScheduler rather than the generic
 * cron, because each call takes a keyword argument.
 *
 * Configuration:
 *   app.ingest.jsearch.enabled=true|false
 *   app.ingest.jsearch.rapidapi-key=...
 *   app.ingest.jsearch.country=ae
 *   app.ingest.jsearch.pages=1
 */
@Slf4j
@Component
public class JSearchSource {

    private final RestTemplate http;

    @Value("${app.ingest.jsearch.enabled:false}")
    private boolean enabled;

    @Value("${app.ingest.jsearch.rapidapi-key:}")
    private String rapidapiKey;

    @Value("${app.ingest.jsearch.country:ae}")
    private String country;

    @Value("${app.ingest.jsearch.pages:1}")
    private int pages;

    /**
     * JSearch date_posted filter. Use "3days" for daily cron runs (far fewer
     * duplicates, smaller responses). Use "month" only for an initial backfill.
     * Valid values: today | 3days | week | month
     */
    @Value("${app.ingest.jsearch.date-posted:3days}")
    private String datePosted;

    /**
     * Comma-separated list of site domains to constrain the JSearch query
     * with Google's {@code site:} operator (e.g.
     * {@code linkedin.com,bayt.com,gulftalent.com,naukrigulf.com,indeed.com}).
     * Empty disables the constraint and lets JSearch return jobs from any
     * aggregated publisher. Filtering at request-time saves RapidAPI quota
     * vs. fetching everything and rejecting in the pipeline.
     */
    @Value("${app.ingest.jsearch.site-restrict:}")
    private String siteRestrict;

    public JSearchSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isEnabled() {
        return enabled && rapidapiKey != null && !rapidapiKey.isBlank();
    }

    /**
     * Wrap the keyword with Google {@code site:} operators so JSearch only
     * returns hits from the configured publishers. Returns the bare keyword
     * when {@link #siteRestrict} is blank.
     *
     * <p>Example: keyword {@code "QA Engineer UAE"} with site-restrict
     * {@code "linkedin.com,bayt.com"} becomes
     * {@code "QA Engineer UAE (site:linkedin.com OR site:bayt.com)"}.
     */
    String buildQuery(String keyword) {
        if (siteRestrict == null || siteRestrict.isBlank()) return keyword;
        String[] sites = siteRestrict.split(",");
        StringBuilder ops = new StringBuilder();
        boolean first = true;
        for (String s : sites) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            if (!first) ops.append(" OR ");
            ops.append("site:").append(trimmed);
            first = false;
        }
        if (ops.length() == 0) return keyword;
        return keyword + " (" + ops + ")";
    }

    public List<IngestedJob> search(String keyword) {
        if (!isEnabled()) return List.of();
        List<IngestedJob> all = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            try {
                all.addAll(fetchPage(keyword, page));
            } catch (Exception ex) {
                log.warn("JSearch keyword='{}' page={} failed: {}", keyword, page, ex.getMessage());
                break;
            }
        }
        log.info("JSearch [{}]: fetched {} job(s).", keyword, all.size());
        return all;
    }

    private List<IngestedJob> fetchPage(String keyword, int page) {
        // Build a properly-encoded URI (avoids spaces/special chars in keyword
        // causing issues when RestTemplate creates the underlying java.net.URI).
        URI uri = UriComponentsBuilder
                .fromUriString("https://jsearch.p.rapidapi.com/search-v2")
                .queryParam("query", buildQuery(keyword))
                .queryParam("page", page)
                .queryParam("num_pages", 1)
                .queryParam("country", country)
                .queryParam("language", "en")       // force English content
                .queryParam("date_posted", datePosted)
                .encode()   // encode each component individually (handles spaces etc.)
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", rapidapiKey);
        headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");
        headers.set("Accept", "application/json");

        ResponseEntity<JsonNode> response = http.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null) {
            log.warn("JSearch keyword='{}' page={}: empty response body", keyword, page);
            return List.of();
        }

        // Check for API-level errors (quota exceeded, invalid key, rate-limit, …).
        // JSearch returns HTTP 200 even for these; the "status" field signals failure.
        String status = body.has("status") ? body.get("status").asText("") : "";
        if (!"OK".equalsIgnoreCase(status)) {
            String msg = body.has("message") ? body.get("message").asText("") : body.toPrettyString();
            log.warn("JSearch keyword='{}' page={}: API error — status='{}' message='{}'",
                    keyword, page, status,
                    msg.length() > 300 ? msg.substring(0, 300) + "…" : msg);
            return List.of();
        }

        // /search-v2 returns { status, data: { jobs: [...] } }
        // /search   returned { status, data: [...] } — keep both paths.
        JsonNode dataNode = body.get("data");
        JsonNode jobsArr;
        if (dataNode == null || dataNode.isNull()) {
            log.warn("JSearch keyword='{}' page={}: 'data' field missing/null. Full body: {}",
                    keyword, page, body.toPrettyString().substring(0, Math.min(500, body.toPrettyString().length())));
            return List.of();
        }
        if (dataNode.isArray()) {
            jobsArr = dataNode;                                  // /search and /search-v2
        } else if (dataNode.has("jobs") && dataNode.get("jobs").isArray()) {
            jobsArr = dataNode.get("jobs");                      // /search-v2 nested variant
        } else {
            log.warn("JSearch keyword='{}' page={}: unexpected 'data' shape: {}",
                    keyword, page, dataNode.toPrettyString().substring(0, Math.min(300, dataNode.toPrettyString().length())));
            return List.of();
        }

        List<IngestedJob> mapped = new ArrayList<>();
        for (JsonNode node : jobsArr) {
            IngestedJob job = mapOne(node);
            if (job != null) mapped.add(job);
        }
        if (mapped.isEmpty() && jobsArr.size() > 0) {
            log.warn("JSearch keyword='{}' page={}: {} job(s) returned but all dropped by mapOne "
                    + "(job_id/job_title/job_apply_link null for every entry).", keyword, page, jobsArr.size());
        }
        return mapped;
    }

    private IngestedJob mapOne(JsonNode node) {
        String id = optText(node, "job_id");
        String title = optText(node, "job_title");
        // /search-v2 prefers job_apply_link; fall back to first apply_options entry.
        String url = firstNonBlank(
                optText(node, "job_apply_link"),
                node.path("apply_options").isArray() && node.path("apply_options").size() > 0
                        ? optText(node.path("apply_options").get(0), "apply_link") : null,
                optText(node, "job_google_link"));
        if (id == null || title == null || url == null) return null;

        String company = optText(node, "employer_name");

        // Prefer the structured job_highlights when JSearch populates it —
        // it's a dict of {Qualifications: [...], Responsibilities: [...],
        // Benefits: [...]} which renders beautifully. Fall back to the
        // free-text job_description otherwise.
        String highlightsBlock = formatJobHighlights(node.path("job_highlights"));
        String rawDesc = optText(node, "job_description");
        String description;
        if (!highlightsBlock.isBlank() && rawDesc != null) {
            description = highlightsBlock + "\n\n" + rawDesc;
        } else if (!highlightsBlock.isBlank()) {
            description = highlightsBlock;
        } else {
            description = rawDesc;
        }
        String publisher = firstNonBlank(
                optText(node, "job_publisher"),
                node.path("apply_options").isArray() && node.path("apply_options").size() > 0
                        ? optText(node.path("apply_options").get(0), "publisher") : null);

        // v2 returns null job_city/job_state — extract from job_location free-text instead.
        String city = firstNonBlank(
                optText(node, "job_city"),
                optText(node, "job_state"),
                extractCityFromLocation(optText(node, "job_location")));
        // v2 also nulls job_country — but we queried country=ae, so it's AE.
        String countryCode = firstNonBlank(optText(node, "job_country"), "AE");
        boolean remote = node.path("job_is_remote").asBoolean(false);

        Integer salaryMin = node.has("job_min_salary") && !node.get("job_min_salary").isNull()
                ? (int) Math.round(node.get("job_min_salary").asDouble()) : null;
        Integer salaryMax = node.has("job_max_salary") && !node.get("job_max_salary").isNull()
                ? (int) Math.round(node.get("job_max_salary").asDouble()) : null;
        String currency = optText(node, "job_salary_currency");
        if (currency == null) currency = "AED";

        // v2 returns job_employment_types[] in English; job_employment_type is localized.
        String empType = null;
        JsonNode typesArr = node.path("job_employment_types");
        if (typesArr.isArray() && typesArr.size() > 0) {
            empType = typesArr.get(0).asText();
        }
        if (empType == null) empType = optText(node, "job_employment_type");
        String jobType = mapEmploymentType(empType);

        String location = (city == null ? "" : city)
                + (countryCode == null ? "" : ", " + countryCode);
        if (location.isBlank()) location = "United Arab Emirates";

        // JSearch provides job_posted_at_datetime_utc ("2024-01-25 00:00:00")
        // and job_posted_at_timestamp (unix epoch seconds) so we can show
        // the real posting date rather than the ingest timestamp.
        OffsetDateTime postedAt = parseJSearchDate(
                optText(node, "job_posted_at_datetime_utc"),
                node.has("job_posted_at_timestamp") && !node.get("job_posted_at_timestamp").isNull()
                        ? node.get("job_posted_at_timestamp").asLong() : null);

        return new IngestedJob(
                id,
                "jsearch",
                publisher,
                title,
                company == null ? "Unknown" : company,
                description == null ? title : description,
                null,
                location,
                inferEmirate(city, optText(node, "job_state")),
                salaryMin,
                salaryMax,
                currency,
                jobType,
                inferExperience(title),
                url,
                remote,
                postedAt,
                null  // JSearch aggregates many boards — not a LinkedIn-specific signal
        );
    }

    /**
     * Render JSearch's structured `job_highlights` dict (Qualifications,
     * Responsibilities, Benefits…) into clean section-headed text that
     * the formatter can pass straight through to the UI.
     */
    private static String formatJobHighlights(JsonNode highlights) {
        if (highlights == null || !highlights.isObject() || highlights.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        var fields = highlights.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode list = entry.getValue();
            if (list == null || !list.isArray() || list.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(entry.getKey()).append(':');
            for (JsonNode item : list) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) sb.append(' ').append(text).append('.');
            }
        }
        return sb.toString();
    }

    /**
     * v2's job_location is a free-text string like "Dubai • via Indeed" or
     * "Abu Dhabi, UAE". Extract the leading place name (everything before
     * the first separator).
     */
    private static String extractCityFromLocation(String loc) {
        if (loc == null || loc.isBlank()) return null;
        // Split on common separators: •, |, "via", "-"
        String first = loc.split("[•|\\-]| via ", 2)[0].trim();
        // Strip a trailing country suffix (", UAE" / ", United Arab Emirates")
        return first.replaceAll("(?i),\\s*(uae|united arab emirates)\\s*$", "").trim();
    }

    private static String mapEmploymentType(String et) {
        if (et == null) return "full_time";
        return switch (et.toUpperCase(Locale.ROOT)) {
            case "FULLTIME"   -> "full_time";
            case "PARTTIME"   -> "part_time";
            case "CONTRACTOR" -> "contract";
            case "INTERN"     -> "internship";
            default           -> "full_time";
        };
    }

    private static String inferEmirate(String city, String state) {
        String haystack = ((city == null ? "" : city) + " " + (state == null ? "" : state)).toLowerCase(Locale.ROOT);
        if (haystack.contains("dubai"))            return "dubai";
        if (haystack.contains("abu dhabi"))        return "abu_dhabi";
        if (haystack.contains("sharjah"))          return "sharjah";
        if (haystack.contains("ajman"))            return "ajman";
        if (haystack.contains("ras al khaimah"))   return "ras_al_khaimah";
        if (haystack.contains("fujairah"))         return "fujairah";
        if (haystack.contains("umm al quwain"))    return "umm_al_quwain";
        return null;
    }

    private static String inferExperience(String title) {
        if (title == null) return "mid_3_5_yrs";
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("senior") || t.contains("lead") || t.contains("principal") || t.contains("staff"))
            return "senior_5_plus";
        if (t.contains("junior") || t.contains("entry") || t.contains("graduate")) return "junior_1_2_yrs";
        if (t.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static final DateTimeFormatter JSEARCH_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Parses JSearch's UTC datetime string or unix-epoch-second fallback. */
    private static OffsetDateTime parseJSearchDate(String datetimeUtc, Long epochSeconds) {
        if (datetimeUtc != null && !datetimeUtc.isBlank()) {
            try {
                return LocalDateTime.parse(datetimeUtc, JSEARCH_DT).atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) { /* fall through */ }
        }
        if (epochSeconds != null && epochSeconds > 0) {
            try {
                return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) { /* fall through */ }
        }
        return null;
    }

    private static String optText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? null : v;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
