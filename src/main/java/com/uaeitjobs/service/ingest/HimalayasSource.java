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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls global remote tech jobs from Himalayas's public API.
 * https://himalayas.app/jobs/api
 *
 * Free, no auth. Roles here are remote-friendly — UAE-based engineers
 * can apply to almost any of them, so each posting is marked
 * remoteUae=true and appears under the existing Remote-UAE filter.
 */
@Slf4j
@Component
public class HimalayasSource implements JobIngestSource {

    private final RestTemplate http;

    @Value("${app.ingest.himalayas.enabled:false}")
    private boolean enabled;

    public HimalayasSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String name() {
        return "himalayas";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<IngestedJob> fetch() {
        if (!enabled) return List.of();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (compatible; UAEITJOBS/1.0; +https://www.uaeitjobs.com)");
            headers.set("Accept", "application/json");
            ResponseEntity<JsonNode> response = http.exchange(
                    "https://himalayas.app/jobs/api?limit=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null) {
                log.warn("Himalayas returned empty body.");
                return List.of();
            }
            JsonNode jobs = body.has("jobs") ? body.get("jobs") : body;
            if (!jobs.isArray()) {
                log.warn("Himalayas response had no jobs array.");
                return List.of();
            }
            List<IngestedJob> mapped = new ArrayList<>();
            for (JsonNode node : jobs) {
                IngestedJob job = mapOne(node);
                if (job != null) mapped.add(job);
            }
            log.info("Himalayas source: fetched {} job(s).", mapped.size());
            return mapped;
        } catch (Exception ex) {
            log.warn("Himalayas fetch failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private IngestedJob mapOne(JsonNode node) {
        String externalId = node.path("guid").asText(node.path("id").asText(null));
        String title = node.path("title").asText(null);
        if (externalId == null || title == null) return null;

        String company = node.path("companyName").asText(node.path("company").asText("Unknown"));
        String applyUrl = node.path("applicationLink").asText(node.path("url").asText(null));
        if (applyUrl == null || applyUrl.isBlank()) {
            String slug = node.path("slug").asText(null);
            if (slug != null) applyUrl = "https://himalayas.app/jobs/" + slug;
        }
        if (applyUrl == null) return null;

        String description = sanitize(node.path("excerpt").asText(""));
        if (description.isBlank()) description = sanitize(node.path("description").asText(title));

        Integer salaryMin = node.has("minSalary") && !node.get("minSalary").isNull()
                ? node.get("minSalary").asInt()
                : null;
        Integer salaryMax = node.has("maxSalary") && !node.get("maxSalary").isNull()
                ? node.get("maxSalary").asInt()
                : null;
        // Himalayas returns USD. Convert roughly to AED for consistency.
        if (salaryMin != null) salaryMin = (int) Math.round(salaryMin * 3.67);
        if (salaryMax != null) salaryMax = (int) Math.round(salaryMax * 3.67);

        return new IngestedJob(
                externalId,
                name(),
                "Himalayas",
                title,
                company,
                description,
                null,
                "Remote (UAE-friendly)",
                null,
                salaryMin,
                salaryMax,
                "AED",
                "full_time",
                inferExperience(title),
                applyUrl,
                true
        );
    }

    private static String inferExperience(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("principal") || lower.contains("staff")) return "senior_5_plus";
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("graduate")) return "junior_1_2_yrs";
        if (lower.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static String sanitize(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
