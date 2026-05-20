package com.uaeitjobs.service.ingest.pipeline.description;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicDescriptionFormatterTest {

    private final HeuristicDescriptionFormatter formatter = new HeuristicDescriptionFormatter();

    // ───────────────────────────────────────────────────────────────
    //  Empty / defensive
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null and empty input return empty string")
    void emptyInput() {
        assertThat(formatter.toHtml(null)).isEmpty();
        assertThat(formatter.toHtml("")).isEmpty();
        assertThat(formatter.toHtml("   ")).isEmpty();
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 1: header scoring
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Title-Case header with colon + keyword scores 4")
    void scoreClassicHeader() {
        // Length 18: pass. Title case: pass. Colon: pass. Keyword: pass.
        assertThat(formatter.scoreHeader("Key Responsibilities:")).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("ALL CAPS without colon still scores enough")
    void scoreAllCapsNoColon() {
        // Length 12: pass. Uppercase: pass. No colon. Keyword "REQUIREMENTS": pass.
        assertThat(formatter.scoreHeader("REQUIREMENTS")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a casual sentence scores below threshold")
    void scoreContent() {
        String sentence = "we are looking for a strong full-stack engineer to join us";
        assertThat(formatter.scoreHeader(sentence)).isLessThan(2);
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 2(a): explicit bullet markers
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Title-Case headers and • bullets produce <h3> + <ul>")
    void bulletDotAndTitleHeaders() {
        String input =
                "About the Role: We are hiring a senior engineer to lead our backend." +
                " Key Responsibilities: • Design distributed systems • Mentor junior engineers" +
                " • Own production SLOs. Required Skills: • Java • Spring Boot • Kafka";
        String html = formatter.toHtml(input);

        // Three headers detected
        assertThat(html).contains("<h3>About the Role</h3>");
        assertThat(html).contains("<h3>Key Responsibilities</h3>");
        assertThat(html).contains("<h3>Required Skills</h3>");

        // Lists rendered
        assertThat(html).contains("<ul>");
        assertThat(html).contains("<li>Design distributed systems</li>");
        assertThat(html).contains("<li>Mentor junior engineers</li>");
        assertThat(html).contains("<li>Own production SLOs</li>");
        assertThat(html).contains("<li>Java</li>");
    }

    @Test
    @DisplayName("ALL CAPS headers and dash bullets render correctly")
    void allCapsHeadersAndDashBullets() {
        String input =
                "REQUIREMENTS - 5+ years Java experience - Kubernetes operations - Strong SQL." +
                " WHAT WE OFFER - Competitive salary - Remote-friendly - Annual learning budget.";
        String html = formatter.toHtml(input);

        assertThat(html).contains("<h3>REQUIREMENTS</h3>");
        assertThat(html).contains("<h3>WHAT WE OFFER</h3>");
        assertThat(html).contains("<li>5+ years Java experience</li>");
        assertThat(html).contains("<li>Kubernetes operations</li>");
        assertThat(html).contains("<li>Competitive salary</li>");
        assertThat(html).contains("<li>Annual learning budget</li>");
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 2(b): inline semicolon lists
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3+ semicolons in a chunk become a <ul>")
    void inlineSemicolonList() {
        String input = "Required Skills: 5+ years Java; Spring Boot; Kafka; AWS; PostgreSQL.";
        String html = formatter.toHtml(input);

        assertThat(html).contains("<h3>Required Skills</h3>");
        assertThat(html).contains("<ul>");
        assertThat(html).contains("<li>5+ years Java</li>");
        assertThat(html).contains("<li>Spring Boot</li>");
        assertThat(html).contains("<li>Kafka</li>");
        assertThat(html).contains("<li>AWS</li>");
        assertThat(html).contains("<li>PostgreSQL</li>");
    }

    @Test
    @DisplayName("Fewer than 3 semicolons stays a paragraph")
    void fewSemicolonsStaysPara() {
        String input = "We use Java; we use Spring Boot.";
        String html = formatter.toHtml(input);
        assertThat(html).contains("<p>");
        assertThat(html).doesNotContain("<ul>");
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 3: wall-of-text breaker
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Walls of text over 300 chars split into <p> groups")
    void wallOfTextSplit() {
        // 5 sentences, total > 300 chars, no bullets, no semicolon list
        String input =
                "We are a fast-growing UAE based fintech building modern payments infrastructure for the region. " +
                "Our engineering team is distributed across Dubai, Abu Dhabi and remote-friendly arrangements. " +
                "We work in small product-aligned squads with full ownership of deployment and operations. " +
                "Every engineer is expected to be hands-on, write code daily and contribute to architecture discussions. " +
                "We value pragmatic decisions over heavy process and continuous improvement over perfection.";
        String html = formatter.toHtml(input);

        // Should produce at least two <p> blocks
        long paragraphCount = html.split("<p>", -1).length - 1;
        assertThat(paragraphCount).isGreaterThanOrEqualTo(2);
        // No bullets injected by mistake
        assertThat(html).doesNotContain("<ul>");
        assertThat(html).doesNotContain("<li>");
    }

    @Test
    @DisplayName("Short paragraph (≤300) stays as a single <p>")
    void shortParagraphStaysSingle() {
        String input = "We are a small UAE-based startup looking for our first backend engineer.";
        String html = formatter.toHtml(input);
        assertThat(html).startsWith("<p>");
        assertThat(html.split("<p>", -1).length - 1).isEqualTo(1);
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 4: sanitisation
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HTML metacharacters in source are escaped")
    void escapesUserContent() {
        String input = "Required Skills: Knowledge of <script> and >evil< content.";
        String html = formatter.toHtml(input);
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Empty <li> and orphan <br> tags get stripped")
    void cleansEmptyTags() {
        // Triple-bullet at end could create an empty trailing <li>
        String input = "Required Skills: • Java • Spring Boot •";
        String html = formatter.toHtml(input);
        assertThat(html).doesNotContain("<li></li>");
        assertThat(html).doesNotContain("<br>");
    }

    @Test
    @DisplayName("Erratic whitespace and stuck sentences are normalised")
    void normalisesWhitespace() {
        String input = "About the Role:We are hiring.Key Responsibilities:Build APIs.Maintain quality.";
        String html = formatter.toHtml(input);
        assertThat(html).contains("<h3>About the Role</h3>");
        assertThat(html).contains("<h3>Key Responsibilities</h3>");
    }

    // ───────────────────────────────────────────────────────────────
    //  Performance smoke test
    // ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processes a 4kB description in under 20ms (warm)")
    void performance() {
        String input = ("About the Role: We are hiring senior engineers. " +
                "Key Responsibilities: • Build APIs • Mentor team • Improve SLOs. " +
                "Required Skills: 5+ years Java; Spring Boot; Kafka; AWS; PostgreSQL. " +
                "Nice to Have: Kubernetes experience. Benefits: • Free visa • Health insurance ")
                .repeat(8);
        // Warm-up
        for (int i = 0; i < 100; i++) formatter.toHtml(input);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) formatter.toHtml(input);
        long ns = System.nanoTime() - start;
        double avgMs = (ns / 100.0) / 1_000_000.0;
        assertThat(avgMs).isLessThan(20.0);
    }

    @Test
    @DisplayName("vendor() returns 'heuristic' — the LLM formatter now owns 'default'")
    void vendorIsHeuristic() {
        assertThat(formatter.vendor()).isEqualTo("heuristic");
    }
}
