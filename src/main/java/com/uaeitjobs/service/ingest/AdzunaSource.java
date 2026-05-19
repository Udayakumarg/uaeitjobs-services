package com.uaeitjobs.service.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls UAE-related IT jobs from the Adzuna Jobs API.
 *
 * Adzuna does NOT support the "ae" country code directly. Workaround:
 * point at the UK ("gb") index and filter via `what_phrase` for UAE
 * keywords. This catches multinational employers (HSBC, EY, Deloitte,
 * BCG, Accenture, Standard Chartered, etc.) that post UAE roles on the
 * UK Adzuna feed.
 *
 * https://developer.adzuna.com/docs/search
 *
 * Free tier: 1000 calls/day per registered app. We pull 50 jobs per page
 * across the first three pages per phrase = ~300 calls per pass which
 * keeps us well inside the quota with a 6-hour cron.
 *
 * Configuration (application.yml / env vars):
 *   app.ingest.adzuna.enabled=true|false
 *   app.ingest.adzuna.app-id=...
 *   app.ingest.adzuna.app-key=...
 *   app.ingest.adzuna.pages=3
 */
@Slf4j
@Component
public class AdzunaSource implements JobIngestSource {

    /** Adzuna does not have an AE index, so we use GB and filter by phrase. */
    private static final String COUNTRY = "gb";
    private static final String CATEGORY_IT = "it-jobs";
    private static final int RESULTS_PER_PAGE = 50;
    /** Phrases that catch UAE-related postings on the UK index. */
    private static final List<String> SEARCH_PHRASES = List.of(
            "United Arab Emirates",
            "Dubai",
            "Abu Dhabi"
    );

    private final RestTemplate http;

    @Value("${app.ingest.adzuna.enabled:false}")
    private boolean enabled;

    @Value("${app.ingest.adzuna.app-id:}")
    private String appId;

    @Value("${app.ingest.adzuna.app-key:}")
    private String appKey;

    @Value("${app.ingest.adzuna.pages:3}")
    private int pages;

    public AdzunaSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String name() {
        return "adzuna";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !appId.isBlank() && !appKey.isBlank();
    }

    @Override
    public List<IngestedJob> fetch() {
        if (!isEnabled()) {
            log.debug("Adzuna source disabled or credentials missing — skipping.");
            return List.of();
        }
        // De-dupe within this pass: phrases can overlap (Dubai + UAE often
        // match the same listing) so we keep a Set of seen apply URLs.
        java.util.LinkedHashMap<String, IngestedJob> deduped = new java.util.LinkedHashMap<>();
        for (String phrase : SEARCH_PHRASES) {
            for (int page = 1; page <= Math.max(1, pages); page++) {
                try {
                    List<IngestedJob> batch = fetchPage(phrase, page);
                    if (batch.isEmpty()) break; // no more pages for this phrase
                    for (IngestedJob job : batch) {
                        if (job.applyUrl() == null) continue;
                        deduped.putIfAbsent(job.applyUrl(), job);
                    }
                } catch (Exception ex) {
                    log.warn("Adzuna phrase='{}' page={} failed: {}", phrase, page, ex.getMessage());
                    break;
                }
            }
        }
        log.info("Adzuna source: fetched {} unique job(s) across {} phrase(s).",
                deduped.size(), SEARCH_PHRASES.size());
        return new ArrayList<>(deduped.values());
    }

    private List<IngestedJob> fetchPage(String phrase, int page) {
        String url = UriComponentsBuilder
                .fromUriString("https://api.adzuna.com/v1/api/jobs/" + COUNTRY + "/search/" + page)
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("category", CATEGORY_IT)
                .queryParam("results_per_page", RESULTS_PER_PAGE)
                .queryParam("what_phrase", phrase)
                .queryParam("content-type", "application/json")
                .toUriString();
        JsonNode body = http.getForObject(url, JsonNode.class);
        if (body == null || !body.has("results")) return List.of();
        JsonNode results = body.get("results");
        List<IngestedJob> mapped = new ArrayList<>(results.size());
        for (JsonNode node : results) {
            IngestedJob job = mapOne(node);
            if (job != null && mentionsUae(job)) mapped.add(job);
        }
        return mapped;
    }

    /** Defensive filter — Adzuna's UK index returns UK-only roles whose
     *  only UAE link is a stray description mention. We require the
     *  LOCATION field to actually reference UAE (titles and descriptions
     *  are too noisy). */
    private static boolean mentionsUae(IngestedJob job) {
        String location = (job.locationUae() == null ? "" : job.locationUae()).toLowerCase(Locale.ROOT);
        return location.contains("united arab emirates")
                || location.contains("uae")
                || location.contains("dubai")
                || location.contains("abu dhabi")
                || location.contains("sharjah")
                || location.contains("ajman")
                || location.contains("ras al khaimah")
                || location.contains("fujairah")
                || location.contains("umm al quwain");
    }

    private IngestedJob mapOne(JsonNode node) {
        String externalId = optString(node, "id");
        String title = optString(node, "title");
        String redirect = optString(node, "redirect_url");
        if (externalId == null || title == null || redirect == null) return null;

        String company = node.path("company").path("display_name").asText("Unknown company");
        String locationFull = node.path("location").path("display_name").asText("United Arab Emirates");
        String emirate = inferEmirate(locationFull, node.path("location").path("area"));
        String description = sanitize(optString(node, "description"));

        Integer salaryMin = node.has("salary_min") && !node.get("salary_min").isNull()
                ? (int) Math.round(node.get("salary_min").asDouble())
                : null;
        Integer salaryMax = node.has("salary_max") && !node.get("salary_max").isNull()
                ? (int) Math.round(node.get("salary_max").asDouble())
                : null;

        String contract = optString(node, "contract_time");
        String jobType = switch (contract == null ? "" : contract) {
            case "full_time" -> "full_time";
            case "part_time" -> "part_time";
            default -> "full_time";
        };

        return new IngestedJob(
                externalId,
                name(),
                title,
                company,
                description == null || description.isBlank() ? title : description,
                null,
                locationFull,
                emirate,
                salaryMin,
                salaryMax,
                "AED",
                jobType,
                inferExperience(title),
                redirect,
                false
        );
    }

    /** Best-effort guess from a free-text location string. */
    private static String inferEmirate(String fullName, JsonNode area) {
        String haystack = (fullName + " " + area.toString()).toLowerCase(Locale.ROOT);
        if (haystack.contains("dubai")) return "dubai";
        if (haystack.contains("abu dhabi")) return "abu_dhabi";
        if (haystack.contains("sharjah")) return "sharjah";
        if (haystack.contains("ajman")) return "ajman";
        if (haystack.contains("ras al khaimah") || haystack.contains("ras al-khaimah")) return "ras_al_khaimah";
        if (haystack.contains("fujairah")) return "fujairah";
        if (haystack.contains("umm al quwain") || haystack.contains("umm al-quwain")) return "umm_al_quwain";
        return null;
    }

    private static String inferExperience(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("principal")) return "senior_5_plus";
        if (lower.contains("junior") || lower.contains("graduate") || lower.contains("entry")) return "junior_1_2_yrs";
        if (lower.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static String sanitize(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String optString(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String value = node.get(field).asText();
        return (value == null || value.isBlank()) ? null : value;
    }
}
