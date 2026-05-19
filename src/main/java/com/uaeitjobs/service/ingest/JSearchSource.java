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

import java.time.Duration;
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

    public JSearchSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isEnabled() {
        return enabled && rapidapiKey != null && !rapidapiKey.isBlank();
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
        String url = UriComponentsBuilder
                .fromUriString("https://jsearch.p.rapidapi.com/search-v2")
                .queryParam("query", keyword)
                .queryParam("page", page)
                .queryParam("num_pages", 1)
                .queryParam("country", country)
                .queryParam("language", "en")       // force English content
                .queryParam("date_posted", "month")
                .build(false)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", rapidapiKey);
        headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");
        headers.set("Accept", "application/json");

        ResponseEntity<JsonNode> response = http.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null) return List.of();

        // /search-v2 returns { status, data: { jobs: [...] } }
        // /search   returned { status, data: [...] } — keep both paths.
        JsonNode dataNode = body.get("data");
        JsonNode jobsArr;
        if (dataNode == null) return List.of();
        if (dataNode.isArray()) {
            jobsArr = dataNode;                                  // legacy /search
        } else if (dataNode.has("jobs") && dataNode.get("jobs").isArray()) {
            jobsArr = dataNode.get("jobs");                      // /search-v2
        } else {
            return List.of();
        }

        List<IngestedJob> mapped = new ArrayList<>();
        for (JsonNode node : jobsArr) {
            IngestedJob job = mapOne(node);
            if (job != null) mapped.add(job);
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
        String description = optText(node, "job_description");
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
                remote
        );
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
