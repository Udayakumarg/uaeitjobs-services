package com.uaeitjobs.service.ingest.pipeline.description.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gemini Flash adapter — built on Java 11+'s native HttpClient so we
 * sidestep Spring's converter chain entirely (which mis-reports
 * application/octet-stream responses as extraction errors).
 *
 * Default model: gemini-2.5-flash (the gemini-1.5-flash model was retired
 * from v1beta in mid-2025).
 *
 * Docs: https://ai.google.dev/api/rest/v1beta/models/generateContent
 */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String DEFAULT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiLlmClient(LlmConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2))))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String model = config.model().isBlank() ? DEFAULT_MODEL : config.model();
        String base  = config.apiUrl().isBlank() ? String.format(DEFAULT_URL, model) : config.apiUrl();
        String url   = base + (base.contains("?") ? "&" : "?") + "key=" + URLEncoder.encode(config.apiKey(), StandardCharsets.UTF_8);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(Map.of(
                        "role",  "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )),
                "generationConfig", Map.of(
                        "temperature",     config.temperature(),
                        "maxOutputTokens", config.maxOutputTokens(),
                        "topP",            0.8,
                        "candidateCount",  1
                )
        );
        byte[] requestBytes = objectMapper.writeValueAsBytes(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String responseBody = new String(response.body(), StandardCharsets.UTF_8);

        if (response.statusCode() >= 400) {
            try {
                JsonNode err = objectMapper.readTree(responseBody).path("error");
                if (!err.isMissingNode()) {
                    throw new IllegalStateException(String.format(
                            "Gemini HTTP %d %s (%s): %s",
                            response.statusCode(),
                            err.path("code").asText("?"),
                            err.path("status").asText("?"),
                            err.path("message").asText("")));
                }
            } catch (IllegalStateException rethrow) {
                throw rethrow;
            } catch (Exception ignore) {
                // payload wasn't JSON — fall through
            }
            throw new IllegalStateException(
                    "Gemini HTTP " + response.statusCode() + ": " + truncate(responseBody, 240));
        }

        if (responseBody.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception parseEx) {
            throw new IllegalStateException(
                    "Gemini returned non-JSON body: " + truncate(responseBody, 200), parseEx);
        }

        if (root.has("error")) {
            JsonNode err = root.path("error");
            throw new IllegalStateException(String.format(
                    "Gemini error %s (%s): %s",
                    err.path("code").asText("?"),
                    err.path("status").asText("?"),
                    err.path("message").asText(responseBody)));
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            JsonNode feedback = root.path("promptFeedback");
            String reason = feedback.isMissingNode() ? responseBody : feedback.toString();
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
