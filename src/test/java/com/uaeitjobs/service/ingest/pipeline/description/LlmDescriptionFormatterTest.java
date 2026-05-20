package com.uaeitjobs.service.ingest.pipeline.description;

import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmClient;
import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDescriptionFormatterTest {

    private static final HeuristicDescriptionFormatter HEURISTIC = new HeuristicDescriptionFormatter();

    private static LlmConfig enabledConfig() {
        return new LlmConfig(true, "gemini", "", "fake-key", "", 8000, 8000, 0.1, 2048);
    }

    private static LlmConfig disabledConfig() {
        return new LlmConfig(false, "gemini", "", "", "", 8000, 8000, 0.1, 2048);
    }

    private static LlmClient mock(String name, String response, boolean shouldThrow) {
        return new LlmClient() {
            @Override public String name() { return name; }
            @Override public String complete(String systemPrompt, String userPrompt) throws Exception {
                if (shouldThrow) throw new RuntimeException("simulated upstream failure");
                return response;
            }
        };
    }

    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disabled config bypasses the LLM entirely and uses the heuristic")
    void disabledFallsBack() {
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                disabledConfig(), HEURISTIC, List.of(mock("gemini", "ignored", false)));
        // 3 semicolons → heuristic semicolon-list rule fires
        String html = f.toHtml("Required Skills: Java; Spring Boot; Kafka; AWS.");
        assertThat(html).contains("<h3>Required Skills</h3>");
        assertThat(html).contains("<li>Java</li>");
    }

    @Test
    @DisplayName("missing API key bypasses the LLM and uses the heuristic")
    void missingKeyFallsBack() {
        LlmConfig cfg = new LlmConfig(true, "gemini", "", "  ", "", 8000, 8000, 0.1, 2048);
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                cfg, HEURISTIC, List.of(mock("gemini", "ignored", false)));
        String html = f.toHtml("REQUIREMENTS - 5+ years Java - AWS.");
        assertThat(html).contains("<h3>REQUIREMENTS</h3>");
    }

    @Test
    @DisplayName("provider mismatch bypasses the LLM and uses the heuristic")
    void unknownProviderFallsBack() {
        LlmConfig cfg = new LlmConfig(true, "ollama", "", "key", "", 8000, 8000, 0.1, 2048);
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                cfg, HEURISTIC, List.of(mock("gemini", "ignored", false)));
        String html = f.toHtml("Key Responsibilities: Build APIs.");
        assertThat(html).contains("<h3>Key Responsibilities</h3>");
    }

    @Test
    @DisplayName("LLM exception is swallowed; heuristic fallback runs")
    void exceptionFallsBack() {
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", "", true)));
        String html = f.toHtml("Required Skills: Java; Spring Boot; Kafka; AWS.");
        assertThat(html).contains("<h3>Required Skills</h3>");
        assertThat(html).contains("<li>Java</li>");
    }

    @Test
    @DisplayName("successful LLM response is returned as-is after sanitisation")
    void successfulResponseReturned() {
        String llmHtml = "<h3>Responsibilities</h3><ul><li>Build APIs</li><li>Mentor team</li></ul>";
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", llmHtml, false)));
        String html = f.toHtml("anything");
        assertThat(html).isEqualTo(llmHtml);
    }

    @Test
    @DisplayName("markdown code-fence wrappers are stripped from LLM responses")
    void stripsMarkdownFences() {
        String llmHtml = "```html\n<h3>Responsibilities</h3><p>Build great products.</p>\n```";
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", llmHtml, false)));
        String html = f.toHtml("anything");
        assertThat(html).startsWith("<h3>");
        assertThat(html).doesNotContain("```");
    }

    @Test
    @DisplayName("cosmetic tags are stripped but inner text preserved")
    void stripsCosmeticTags() {
        // <div> wraps legit content — drop the tag, keep the text. Output
        // begins with a real allowed tag so the formatter accepts it.
        String llmHtml = "<h3>Skills</h3><ul><li>Java</li></ul><div>Extra context.</div>";
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", llmHtml, false)));
        String html = f.toHtml("anything");
        assertThat(html).doesNotContain("<div");
        assertThat(html).contains("<h3>Skills</h3>");
        assertThat(html).contains("Extra context.");
    }

    @Test
    @DisplayName("dangerous tag payloads (script, iframe) are removed entirely")
    void stripsDangerousPayloads() {
        // Output must NEVER contain the script payload, irrespective of
        // whether the formatter accepts the cleaned HTML or falls back.
        String llmHtml = "<script>alert('xss')</script><h3>OK</h3><p>safe</p>";
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", llmHtml, false)));
        String html = f.toHtml("Required Skills: Java; Spring Boot; Kafka; AWS.");
        assertThat(html).doesNotContain("<script");
        assertThat(html).doesNotContain("alert");
    }

    @Test
    @DisplayName("LLM returning non-HTML prose triggers heuristic fallback")
    void nonHtmlResponseFallsBack() {
        String llmReply = "I'm sorry, I cannot help with that.";
        LlmDescriptionFormatter f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", llmReply, false)));
        String html = f.toHtml("Required Skills: Java; Spring; Kafka.");
        // heuristic kicked in
        assertThat(html).contains("<h3>Required Skills</h3>");
    }

    @Test
    @DisplayName("empty input returns empty string without touching the LLM")
    void emptyInput() {
        var f = new LlmDescriptionFormatter(
                enabledConfig(), HEURISTIC,
                List.of(mock("gemini", "should-not-be-called", true)));
        assertThat(f.toHtml(null)).isEmpty();
        assertThat(f.toHtml("")).isEmpty();
        assertThat(f.toHtml("   ")).isEmpty();
    }

    @Test
    @DisplayName("vendor() returns 'default' so it owns the registry fallback slot")
    void vendorIsDefault() {
        var f = new LlmDescriptionFormatter(disabledConfig(), HEURISTIC, List.of());
        assertThat(f.vendor()).isEqualTo("default");
    }
}
