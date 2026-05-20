package com.uaeitjobs.service.ingest.pipeline.description.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Claude 3.5 Haiku adapter via the Anthropic Messages API.
 *
 * Docs: https://docs.anthropic.com/en/api/messages
 *
 * Tier 1 free credit gives plenty of room to test; production cost is
 * ~$0.005 per typical job description.
 */
@Slf4j
@Component
public class ClaudeLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "claude-3-5-haiku-20241022";
    private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final LlmConfig config;
    private final RestClient client;

    public ClaudeLlmClient(LlmConfig config) {
        this.config = config;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2)));
        factory.setReadTimeout(config.timeoutMs());
        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
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

        JsonNode response = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key",         config.apiKey())
                .header("anthropic-version", API_VERSION)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Claude returned an empty body");
        }

        // {"content":[{"type":"text","text":"..."}], "stop_reason":"end_turn"}
        JsonNode content = response.path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new IllegalStateException("Claude returned no content: " + truncate(response.toString(), 240));
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
