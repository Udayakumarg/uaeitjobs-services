package com.uaeitjobs.service.ingest.pipeline.description.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API adapter — built on Java 11+'s native HttpClient
 * so we sidestep Spring's converter chain entirely.
 *
 * Default model: claude-3-5-haiku-20241022.
 * Docs: https://docs.anthropic.com/en/api/messages
 */
@Slf4j
@Component
public class ClaudeLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "claude-3-5-haiku-20241022";
    private static final String DEFAULT_URL   = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION   = "2023-06-01";

    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClaudeLlmClient(LlmConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2))))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String url   = config.apiUrl().isBlank() ? DEFAULT_URL : config.apiUrl();
        String model = config.model().isBlank() ? DEFAULT_MODEL : config.model();

        Map<String, Object> body = Map.of(
                "model",       model,
                "max_tokens",  config.maxOutputTokens(),
                "temperature", config.temperature(),
                "system",      systemPrompt,
                "messages",    List.of(Map.of(
                        "role",    "user",
                        "content", userPrompt
                ))
        );
        byte[] requestBytes = objectMapper.writeValueAsBytes(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("x-api-key",         config.apiKey())
                .header("anthropic-version", API_VERSION)
                .header("Content-Type",      "application/json")
                .header("Accept",            "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String responseBody = new String(response.body(), StandardCharsets.UTF_8);

        if (response.statusCode() >= 400) {
            try {
                JsonNode err = objectMapper.readTree(responseBody).path("error");
                if (!err.isMissingNode()) {
                    throw new IllegalStateException(String.format(
                            "Claude HTTP %d (%s): %s",
                            response.statusCode(),
                            err.path("type").asText("?"),
                            err.path("message").asText("")));
                }
            } catch (IllegalStateException rethrow) {
                throw rethrow;
            } catch (Exception ignore) {
                // payload wasn't JSON — fall through
            }
            throw new IllegalStateException(
                    "Claude HTTP " + response.statusCode() + ": " + truncate(responseBody, 240));
        }

        if (responseBody.isBlank()) {
            throw new IllegalStateException("Claude returned an empty body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception parseEx) {
            throw new IllegalStateException(
                    "Claude returned non-JSON body: " + truncate(responseBody, 200), parseEx);
        }

        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new IllegalStateException("Claude returned no content: " + truncate(responseBody, 240));
        }
        String text = content.get(0).path("text").asText("");
        if (text.isBlank()) {
            throw new IllegalStateException("Claude content text is empty");
        }
        return text;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
