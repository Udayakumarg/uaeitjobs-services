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
 * OpenAI Chat Completions adapter — built on Java 11+'s native HttpClient.
 *
 * Avoiding Spring's RestClient here on purpose: its message-converter chain
 * mis-handles upstream Content-Type quirks (e.g. application/octet-stream)
 * and surfaces them as opaque extraction errors. The JDK client gives us
 * a raw byte body that we decode + parse ourselves.
 *
 * Default model is {@code gpt-4o-mini}. Override with {@code app.llm.model}.
 * Docs: https://platform.openai.com/docs/api-reference/chat/create
 */
@Slf4j
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiLlmClient(LlmConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                // Connect-timeout is per-attempt; read happens via request.timeout below.
                .connectTimeout(Duration.ofMillis(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2))))
                // HTTP/2 by default; OpenAI handles both fine. Falling back to HTTP/1.1
                // here would mask any HTTP/2 negotiation issues in the JVM/JDK build.
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String url   = config.apiUrl().isBlank() ? DEFAULT_URL : config.apiUrl();
        String model = config.model().isBlank() ? DEFAULT_MODEL : config.model();

        Map<String, Object> body = Map.of(
                "model",       model,
                "messages",    List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                ),
                "temperature", config.temperature(),
                "max_tokens",  config.maxOutputTokens()
        );
        byte[] requestBytes = objectMapper.writeValueAsBytes(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String responseBody = new String(response.body(), StandardCharsets.UTF_8);

        if (response.statusCode() >= 400) {
            // Try to surface OpenAI's structured error payload if present.
            try {
                JsonNode err = objectMapper.readTree(responseBody).path("error");
                if (!err.isMissingNode()) {
                    throw new IllegalStateException(String.format(
                            "OpenAI HTTP %d %s (%s): %s",
                            response.statusCode(),
                            err.path("code").asText("?"),
                            err.path("type").asText("?"),
                            err.path("message").asText("")));
                }
            } catch (IllegalStateException rethrow) {
                throw rethrow;
            } catch (Exception ignore) {
                // payload wasn't JSON — fall through to generic message
            }
            throw new IllegalStateException(
                    "OpenAI HTTP " + response.statusCode() + ": " + truncate(responseBody, 240));
        }

        if (responseBody.isBlank()) {
            throw new IllegalStateException("OpenAI returned an empty body (status " + response.statusCode() + ")");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception parseEx) {
            throw new IllegalStateException(
                    "OpenAI returned non-JSON body: " + truncate(responseBody, 200), parseEx);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no choices: " + truncate(responseBody, 240));
        }
        String text = choices.get(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new IllegalStateException("OpenAI message content is empty");
        }
        return text;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
