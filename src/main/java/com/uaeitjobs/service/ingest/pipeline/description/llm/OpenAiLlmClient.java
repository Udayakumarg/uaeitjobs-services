package com.uaeitjobs.service.ingest.pipeline.description.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions adapter.
 *
 * Default model is {@code gpt-4o-mini} — the cheapest GPT-4-class model at
 * roughly $0.15 / M input + $0.60 / M output tokens, which is ~$0.0006
 * per typical job description.  Tier 1+ accounts (any prepaid balance)
 * get 500 RPM, more than enough for our ingestion cadence.
 *
 * Docs: https://platform.openai.com/docs/api-reference/chat/create
 */
@Slf4j
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

    private final LlmConfig config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(LlmConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(5_000, Math.max(1_000, config.timeoutMs() / 2)));
        factory.setReadTimeout(config.timeoutMs());
        this.client = RestClient.builder()
                .requestFactory(factory)
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

        // Read as raw String + parse with Jackson so we are immune to upstream
        // Content-Type quirks (we've seen application/octet-stream from Google).
        String responseBody = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.apiKey())
                .body(body)
                .retrieve()
                .body(String.class);

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("OpenAI returned an empty body");
        }

        JsonNode response = objectMapper.readTree(responseBody);

        // OpenAI error envelope: {"error":{"message","type","code"}}
        if (response.has("error")) {
            JsonNode err = response.path("error");
            throw new IllegalStateException(String.format(
                    "OpenAI error %s (%s): %s",
                    err.path("code").asText("?"),
                    err.path("type").asText("?"),
                    err.path("message").asText(response.toString())));
        }

        // {"choices":[{"message":{"role":"assistant","content":"..."}}]}
        JsonNode choices = response.path("choices");
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
