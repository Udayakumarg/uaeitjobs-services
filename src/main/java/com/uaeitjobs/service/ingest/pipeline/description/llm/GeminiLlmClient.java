package com.uaeitjobs.service.ingest.pipeline.description.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gemini 1.5 Flash adapter — the cheapest viable model for this workload
 * (~$0.00033 per typical job description).  Uses the public generative-
 * language endpoint with API-key auth.
 *
 * Docs: https://ai.google.dev/api/rest/v1beta/models/generateContent
 */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    /**
     * Default model. Google retired `gemini-1.5-flash` from the v1beta endpoint
     * in mid-2025 — calls to it now return 404. The current Flash GA models are:
     *   gemini-2.5-flash       ← recommended, used here by default
     *   gemini-2.0-flash       ← stable earlier 2.x series
     *   gemini-2.5-flash-lite  ← cheapest, slightly lower quality
     * Override with {@code app.llm.model} (env var {@code LLM_MODEL}) without
     * touching code.
     */
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String DEFAULT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final LlmConfig config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(LlmConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Connect can be tighter than read — Gemini takes a few seconds to respond.
        factory.setConnectTimeout(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2)));
        factory.setReadTimeout(config.timeoutMs());
        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String model = config.model().isBlank() ? DEFAULT_MODEL : config.model();
        String base = config.apiUrl().isBlank() ? String.format(DEFAULT_URL, model) : config.apiUrl();
        String url = base + (base.contains("?") ? "&" : "?") + "key=" + config.apiKey();

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )),
                "generationConfig", Map.of(
                        "temperature",      config.temperature(),
                        "maxOutputTokens",  config.maxOutputTokens(),
                        "topP",             0.8,
                        "candidateCount",   1
                )
        );

        // Read as raw bytes + decode + parse — byte[] sidesteps Spring's
        // content-type-driven converter chain, so we're immune to upstream
        // serving JSON with application/octet-stream.
        byte[] responseBytes = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(byte[].class);

        if (responseBytes == null || responseBytes.length == 0) {
            throw new IllegalStateException("Gemini returned an empty body");
        }
        String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (Exception parseEx) {
            throw new IllegalStateException(
                    "Gemini returned non-JSON body: " + truncate(responseBody, 200), parseEx);
        }

        // Google error envelope: {"error": {"code", "message", "status"}}
        if (response.has("error")) {
            JsonNode err = response.path("error");
            throw new IllegalStateException(String.format(
                    "Gemini error %s (%s): %s",
                    err.path("code").asText("?"),
                    err.path("status").asText("?"),
                    err.path("message").asText(response.toString())));
        }

        // {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            // Gemini sometimes returns a `promptFeedback` block when it blocks content.
            JsonNode feedback = response.path("promptFeedback");
            String reason = feedback.isMissingNode() ? response.toString() : feedback.toString();
            throw new IllegalStateException("Gemini returned no candidates: " + truncate(reason, 240));
        }
        String text = candidates.get(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText("");
        if (text.isBlank()) {
            throw new IllegalStateException("Gemini candidate text is empty");
        }
        return text;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
