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
 * Pulls remote-worldwide tech jobs from the free RemoteOK API.
 * No auth required.
 *
 * UAE-based developers can apply to these, so we surface them with the
 * remote_uae flag set to true. Filter on the catalog with
 * /jobs?remoteUae=true to see only the remote roster.
 */
@Slf4j
@Component
public class RemoteOkSource implements JobIngestSource {

    private final RestTemplate http;

    @Value("${app.ingest.remoteok.enabled:false}")
    private boolean enabled;

    public RemoteOkSource(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String name() {
        return "remoteok";
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
            // RemoteOK serves 403 to default UA — present as a normal client.
            headers.set("User-Agent", "Mozilla/5.0 (compatible; UAEITJOBS/1.0; +https://www.uaeitjobs.com)");
            headers.set("Accept", "application/json");
            ResponseEntity<JsonNode> response = http.exchange(
                    "https://remoteok.com/api?tags=dev",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null || !body.isArray()) {
                log.warn("RemoteOK returned an unexpected payload.");
                return List.of();
            }
            List<IngestedJob> mapped = new ArrayList<>();
            for (JsonNode node : body) {
                // The API's first entry is a legal/license notice with no id — skip it.
                if (!node.has("id")) continue;
                IngestedJob job = mapOne(node);
                if (job != null) mapped.add(job);
            }
            log.info("RemoteOK source: fetched {} job(s).", mapped.size());
            return mapped;
        } catch (Exception ex) {
            log.warn("RemoteOK fetch failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private IngestedJob mapOne(JsonNode node) {
        String externalId = node.path("id").asText(null);
        String title = node.path("position").asText(null);
        String company = node.path("company").asText("Remote employer");
        String url = node.path("url").asText(null);
        if (externalId == null || title == null || url == null) return null;

        String description = sanitize(node.path("description").asText(""));
        if (description.isBlank()) description = title;

        Integer salaryMin = node.has("salary_min") && !node.get("salary_min").isNull()
                ? node.get("salary_min").asInt()
                : null;
        Integer salaryMax = node.has("salary_max") && !node.get("salary_max").isNull()
                ? node.get("salary_max").asInt()
                : null;
        // RemoteOK returns USD figures. Convert to AED roughly (3.67) so they
        // are comparable with the rest of the catalog.
        if (salaryMin != null) salaryMin = (int) Math.round(salaryMin * 3.67);
        if (salaryMax != null) salaryMax = (int) Math.round(salaryMax * 3.67);

        return new IngestedJob(
                externalId,
                name(),
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
                url,
                true // remote_uae = true, that's the whole point of this source
        );
    }

    private static String inferExperience(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("principal")) return "senior_5_plus";
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("graduate")) return "junior_1_2_yrs";
        if (lower.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static String sanitize(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
