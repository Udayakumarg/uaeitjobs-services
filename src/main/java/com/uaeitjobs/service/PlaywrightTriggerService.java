package com.uaeitjobs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Calls the Node.js scraper trigger server to launch a Playwright scraper
 * source on demand.  The trigger server runs on the same host as the scraper
 * at {@code app.scraper.trigger-url} (default: http://localhost:3001).
 *
 * Sources: bayt | naukrigulf | gulftalent | linkedin
 */
@Slf4j
@Service
public class PlaywrightTriggerService {

    private static final Set<String> VALID_SOURCES =
            Set.of("bayt", "naukrigulf", "gulftalent", "linkedin");

    private final RestTemplate http;

    @Value("${app.scraper.trigger-url:http://localhost:3001}")
    private String triggerUrl;

    @Value("${app.scraper.trigger-secret:}")
    private String triggerSecret;

    public PlaywrightTriggerService(RestTemplateBuilder builder) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public enum TriggerStatus { STARTED, ALREADY_RUNNING, UNAVAILABLE }

    public record TriggerResult(TriggerStatus status, String message) {}

    /**
     * Triggers a scraper run for the given source.
     * Returns immediately — the scraper runs in the background on the host.
     */
    public TriggerResult trigger(String source) {
        if (!VALID_SOURCES.contains(source.toLowerCase())) {
            return new TriggerResult(TriggerStatus.UNAVAILABLE,
                    "Unknown source: " + source + ". Valid: " + VALID_SOURCES);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            if (triggerSecret != null && !triggerSecret.isBlank()) {
                headers.set("X-Trigger-Secret", triggerSecret);
            }

            ResponseEntity<Map> response = http.exchange(
                    triggerUrl + "/trigger/" + source.toLowerCase(),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Map.class
            );

            String status = response.getBody() != null
                    ? String.valueOf(response.getBody().get("status"))
                    : "unknown";

            if ("already_running".equals(status)) {
                return new TriggerResult(TriggerStatus.ALREADY_RUNNING,
                        source + " scraper is already running");
            }

            log.info("Playwright scraper '{}' triggered successfully", source);
            return new TriggerResult(TriggerStatus.STARTED,
                    source + " scraper started");

        } catch (Exception e) {
            log.warn("Could not reach scraper trigger server for '{}': {}", source, e.getMessage());
            return new TriggerResult(TriggerStatus.UNAVAILABLE,
                    "Scraper trigger server unreachable: " + e.getMessage());
        }
    }

    /**
     * Returns a per-source running/idle status map from the trigger server.
     * Returns an empty map when the trigger server is unreachable.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> status() {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (triggerSecret != null && !triggerSecret.isBlank()) {
                headers.set("X-Trigger-Secret", triggerSecret);
            }
            ResponseEntity<Map> response = http.exchange(
                    triggerUrl + "/status",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Object statusObj = response.getBody() != null
                    ? response.getBody().get("status") : null;
            return statusObj instanceof Map ? (Map<String, String>) statusObj : Map.of();
        } catch (Exception e) {
            log.debug("Trigger server status check failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
