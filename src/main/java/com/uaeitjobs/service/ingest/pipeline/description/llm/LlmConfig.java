package com.uaeitjobs.service.ingest.pipeline.description.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the LLM-backed description formatter.
 *
 * Wired from {@code app.llm.*} in application.yml.  Defaults are tuned for
 * the cheapest sensible setup (Gemini 1.5 Flash, 8s read timeout, 8000-char
 * input cap to keep tokens bounded).
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmConfig(
        /** Master kill-switch. When false the formatter immediately falls back to the heuristic. */
        boolean enabled,
        /** Provider identifier. Must match an {@link LlmClient#name()}. e.g. "gemini" or "claude". */
        String provider,
        /** Optional URL override. When blank, the provider client uses its built-in default. */
        String apiUrl,
        /** Credential. Required when {@link #enabled} is true. */
        String apiKey,
        /** Model name override. When blank, the provider client uses its default model. */
        String model,
        /** HTTP read timeout (ms). 5000–10000 is sensible for a job description. */
        int timeoutMs,
        /** Hard cap on raw input length to control token spend. Anything longer is truncated. */
        int maxInputChars,
        /** Generation temperature. Low values (0.0–0.2) keep formatting deterministic. */
        double temperature,
        /** Output token cap. ~2048 covers even very long job descriptions in clean HTML. */
        int maxOutputTokens
) {
    public LlmConfig {
        if (provider == null || provider.isBlank()) provider = "gemini";
        if (apiUrl == null) apiUrl = "";
        if (apiKey == null) apiKey = "";
        if (model == null) model = "";
        if (timeoutMs <= 0) timeoutMs = 8000;
        if (maxInputChars <= 0) maxInputChars = 8000;
        if (temperature < 0) temperature = 0.1;
        if (maxOutputTokens <= 0) maxOutputTokens = 2048;
    }
}
