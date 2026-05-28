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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Pulls UAE IT jobs from the CareerJet public REST API.
 *
 * No API key required. The {@code locale_code=en_AE} parameter constrains
 * results to CareerJet's UAE index so every result is already a UAE job.
 *
 * https://www.careerjet.ae/jobs/api.html
 *
 * Configuration (application.yml / env vars):
 *   app.ingest.careerjet.enabled=true|false
 *   app.ingest.careerjet.pages=2            (max pages per keyword, default 2)
 */
@Slf4j
@Component
public class CareerJetSource implements JobIngestSource {

    private static final String BASE_URL = "https://public.api.careerjet.com/search";
    private static final String LOCALE_AE  = "en_AE";
    private static final int    PAGE_SIZE  = 20;        // CareerJet max per page

    /** IT job keywords to cycle through on each ingestion pass. */
    private static final List<String> KEYWORDS = List.of(
            "software engineer",
            "full stack developer",
            "backend developer",
            "frontend developer",
            "devops engineer",
            "data engineer",
            "cloud engineer",
            "mobile developer",
            "qa engineer",
            "solution architect",
            "it manager",
            "cybersecurity"
    );

    private final RestTemplate http;

    @Value("${app.ingest.careerjet.enabled:false}")
    private boolean enabled;

    @Value("${app.ingest.careerjet.pages:2}")
    private int pages;

    public CareerJetSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String name() { return "careerjet"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public List<IngestedJob> fetch() {
        LinkedHashMap<String, IngestedJob> deduped = new LinkedHashMap<>();

        for (String keyword : KEYWORDS) {
            for (int page = 1; page <= Math.max(1, pages); page++) {
                try {
                    List<IngestedJob> batch = fetchPage(keyword, page);
                    if (batch.isEmpty()) break;
                    for (IngestedJob job : batch) {
                        if (job.applyUrl() != null) {
                            deduped.putIfAbsent(job.applyUrl(), job);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("CareerJet keyword='{}' page={} failed: {}", keyword, page, ex.getMessage());
                    break;
                }
            }
        }

        log.info("CareerJet: fetched {} unique job(s) across {} keyword(s).",
                deduped.size(), KEYWORDS.size());
        return new ArrayList<>(deduped.values());
    }

    private List<IngestedJob> fetchPage(String keyword, int page) {
        String url = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("keywords",    keyword)
                .queryParam("locale_code", LOCALE_AE)
                .queryParam("pagesize",    PAGE_SIZE)
                .queryParam("page",        page)
                .queryParam("sort",        "date")
                .toUriString();

        // CareerJet's CDN blocks bare Java user-agents from datacenter IPs.
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");

        ResponseEntity<JsonNode> resp = http.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = resp.getBody();

        if (body == null) {
            log.warn("CareerJet keyword='{}' page={}: null response body.", keyword, page);
            return List.of();
        }
        if (!body.has("jobs") || !body.get("jobs").isArray()) {
            log.warn("CareerJet keyword='{}' page={}: unexpected response — top-level keys: {}",
                    keyword, page, body.fieldNames());
            return List.of();
        }

        JsonNode jobs = body.get("jobs");
        List<IngestedJob> mapped = new ArrayList<>(jobs.size());
        for (JsonNode node : jobs) {
            IngestedJob job = mapOne(node);
            if (job != null) mapped.add(job);
        }
        log.debug("CareerJet keyword='{}' page={}: {} job(s).", keyword, page, mapped.size());
        return mapped;
    }

    private IngestedJob mapOne(JsonNode node) {
        String url   = optText(node, "url");
        String title = optText(node, "title");
        if (url == null || title == null) return null;

        // CareerJet uses a numeric job ID embedded in the redirect URL.
        // e.g. https://www.careerjet.ae/jobad/123456789
        String externalId = extractId(url);
        if (externalId == null) return null;

        String company  = optText(node, "company");
        String location = optText(node, "locations");   // "Dubai, Dubai, UAE"
        String desc     = optText(node, "description");
        String salary   = optText(node, "salary");      // free-text, not parsed

        String emirate  = inferEmirate(location);

        // Build a clean location string: trim redundant city repetition
        // "Dubai, Dubai, United Arab Emirates" → "Dubai, UAE"
        String cleanLocation = cleanLocation(location);

        OffsetDateTime postedAt = parseDate(optText(node, "date"));

        return new IngestedJob(
                "cj_" + externalId,
                name(),
                "CareerJet",
                title,
                company == null ? "Unknown" : company,
                desc == null ? title : desc,
                null,
                cleanLocation,
                emirate,
                null,
                null,
                "AED",
                "full_time",
                inferExperience(title),
                url,
                false,
                postedAt
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String extractId(String url) {
        if (url == null) return null;
        // Matches /jobad/123456 or /jobad/ae123456
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/jobad/([a-zA-Z0-9]+)")
                .matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String inferEmirate(String loc) {
        if (loc == null) return null;
        String t = loc.toLowerCase(Locale.ROOT);
        if (t.contains("dubai"))          return "dubai";
        if (t.contains("abu dhabi"))      return "abu_dhabi";
        if (t.contains("sharjah"))        return "sharjah";
        if (t.contains("ajman"))          return "ajman";
        if (t.contains("ras al khaimah")) return "ras_al_khaimah";
        if (t.contains("fujairah"))       return "fujairah";
        if (t.contains("umm al quwain"))  return "umm_al_quwain";
        return null;
    }

    /**
     * CareerJet often repeats the city: "Dubai, Dubai, United Arab Emirates".
     * Collapse to "Dubai, UAE".
     */
    private static String cleanLocation(String loc) {
        if (loc == null) return "United Arab Emirates";
        // Normalise the country suffix
        String clean = loc
                .replaceAll("(?i),\\s*United Arab Emirates\\s*$", ", UAE")
                .replaceAll("(?i),\\s*UAE\\s*,\\s*UAE\\s*$", ", UAE");
        // Remove duplicate leading city: "Dubai, Dubai, UAE" → "Dubai, UAE"
        String[] parts = clean.split(",\\s*");
        if (parts.length >= 2 && parts[0].trim().equalsIgnoreCase(parts[1].trim())) {
            clean = parts[0].trim()
                    + (parts.length > 2 ? ", " + String.join(", ", java.util.Arrays.copyOfRange(parts, 2, parts.length)) : "");
        }
        return clean.isBlank() ? "UAE" : clean;
    }

    private static String inferExperience(String title) {
        if (title == null) return "mid_3_5_yrs";
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("senior") || t.contains("lead") || t.contains("principal") || t.contains("staff"))
            return "senior_5_plus";
        if (t.contains("junior") || t.contains("entry") || t.contains("graduate"))
            return "junior_1_2_yrs";
        if (t.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static final DateTimeFormatter CJ_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static OffsetDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw, CJ_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String optText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? null : v;
    }
}
