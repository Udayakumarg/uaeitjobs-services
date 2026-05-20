package com.uaeitjobs.service.ingest.pipeline.description;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default, vendor-neutral formatter.
 *
 * Many aggregators (JSearch, Google for Jobs, Adzuna…) strip source HTML
 * and emit one flat string: headers run into the previous sentence,
 * bullet markers are inconsistent, and lists collapse into semicolons.
 * Rather than guessing per-source quirks, this formatter applies a
 * deterministic heuristic scoring engine that works on any text.
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li><b>Header detection</b> — each chunk (split on periods, newlines,
 *       double spaces, or detected inline-header boundaries) is scored.
 *       A chunk becomes an {@code <h3>} when its score ≥ 2.
 *       <ul>
 *         <li>+1 if length ∈ [5..60]</li>
 *         <li>+1 if &gt;50% uppercase or Title Case</li>
 *         <li>+1 if ends with ":"</li>
 *         <li>+1 if it contains a known domain keyword</li>
 *       </ul>
 *   </li>
 *   <li><b>List normalisation</b> — explicit bullet markers (•, ●, ▪, -,
 *       *, ✓, ➢) split a block into {@code <li>}s. If a sentence
 *       contains ≥ 3 semicolons, it's split on semicolons instead.
 *       Contiguous {@code <li>}s are wrapped in a single {@code <ul>}.</li>
 *   <li><b>Wall-of-text breaker</b> — any content block over 300 chars
 *       is sliced at sentence boundaries ({@code . + space + uppercase})
 *       and grouped 2–3 sentences per {@code <p>} for visual rhythm.</li>
 *   <li><b>Sanitisation</b> — collapses whitespace, removes orphan tags
 *       and empty list items, escapes user text content so the output is
 *       safe to inject into the DOM.</li>
 * </ol>
 *
 * Average execution: ~1ms for a 4 KB description on commodity hardware.
 */
@Component
public class HeuristicDescriptionFormatter implements JobDescriptionFormatter {

    // ─── Pre-compiled patterns ──────────────────────────────────────
    // Every regex is static + thread-safe to keep per-job overhead minimal.

    /** Whitespace-period-Uppercase fixer: "delivery.Key" → "delivery. Key". */
    private static final Pattern STUCK_PERIOD =
            Pattern.compile("\\.([A-Z])");

    /** Colon-Uppercase fixer: "Role:We" → "Role: We". */
    private static final Pattern STUCK_COLON =
            Pattern.compile(":([A-Z])");

    /** Multiple whitespace runs collapse to single space. */
    private static final Pattern WHITESPACE_RUNS =
            Pattern.compile("\\s+");

    /**
     * Sentinel used to mark synthetic chunk boundaries around inline headers.
     * Chosen to be a single character that never appears in real descriptions.
     */
    private static final String SENTINEL = "";

    /**
     * Detects keyword-driven Title-Case headers ending in a colon and
     * wraps them in sentinels so the chunk splitter sees them as their
     * own chunk. Group 1 captures the header text.
     */
    private static final Pattern HEADER_BRACKET = Pattern.compile(
            "(?i)(?<![A-Za-z])(" +
                "About (?:the )?(?:Role|Company|Position|Team|Job|Us|You)|" +
                "(?:Key |Main )?Responsibilities|" +
                "Required (?:Technical )?Skills|Required Skills|Requirements|" +
                "Key (?:Technical )?Skills|Technical Skills|" +
                "Skills (?:and|&) (?:Experience|Qualifications)|" +
                "Nice to Have|Bonus(?: Points)?|Plus(?: Points)?|" +
                "Preferred (?:Qualifications|Skills|Experience)|" +
                "Qualifications|Experience Required|" +
                "Domain Experience|" +
                "Required Competencies|Competencies|" +
                "(?:What We|We) Offer|Benefits|Perks|Compensation|" +
                "How to Apply|Application Process|" +
                "Your (?:Role|Responsibilities|Profile|Main Tasks|Tasks|Day)" +
            ")\\s*:\\s*"
    );

    /**
     * Detects ALL-CAPS headers followed by a space + dash separator (the
     * "REQUIREMENTS - 5+ years..." pattern) and brackets them similarly.
     */
    private static final Pattern HEADER_BRACKET_CAPS = Pattern.compile(
            "(?<![A-Za-z])([A-Z]{3,}(?:\\s+[A-Z]{2,})*)\\s+-\\s+"
    );

    /** Chunk boundary: sentinel, full-stop+space, newline, or double space. */
    private static final Pattern CHUNK_BOUNDARY =
            Pattern.compile("[" + SENTINEL + "]|(?<=\\.) +|\\n+|\\r+|\\t+|  +");

    /** Sentence boundary inside a single chunk: . + space + Uppercase. */
    private static final Pattern SENTENCE_SPLIT =
            Pattern.compile("(?<=\\.) +(?=[A-Z(\\[])");

    /** Any explicit bullet glyph the source might use. */
    private static final Pattern BULLET_SPLIT =
            Pattern.compile("\\s*[•●▪◦■□▶▸►✓✔➢➤*]\\s+|\\s+-\\s+");

    /** Detects whether a string contains an explicit bullet marker. */
    private static final Pattern HAS_BULLET =
            Pattern.compile("[•●▪◦■□▶▸►✓✔➢➤]|(?:^|\\s)[-*]\\s+");

    /** Used to decide if a chunk is in "Title Case". */
    private static final Pattern TITLE_CASE_WORD =
            Pattern.compile("\\b[A-Z][a-z']+\\b");

    /** Word-boundary domain-keyword detector (no false positives in long sentences). */
    private static final Pattern HEADER_KEYWORD_WORD = Pattern.compile(
            "(?i)\\b(" +
                "responsibilities|responsibility|requirements|qualifications|" +
                "skills|tools|profile|benefits|perks|compensation|" +
                "duties|tasks|competencies|" +
                "what we offer|we offer|what you'll do|what you will do|" +
                "nice to have|must have|key responsibilities|" +
                "your role|your day|your tasks|your profile|" +
                "about the role|about the company|about us|about you|" +
                "preferred|required|domain experience|" +
                "how to apply|application process" +
            ")\\b"
    );

    /** Strip empty <li></li> remnants from aggressive splitting. */
    private static final Pattern EMPTY_LI = Pattern.compile("<li>\\s*</li>");

    /** Strip orphan <br> tags. */
    private static final Pattern ORPHAN_BR = Pattern.compile("<br\\s*/?>");

    @Override
    public String vendor() {
        // The LLM-backed formatter now owns the "default" key. The
        // heuristic remains the in-process backup used by LlmDescriptionFormatter
        // on any failure, and can still be addressed explicitly by name.
        return "heuristic";
    }

    @Override
    public String toHtml(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // 1. Pre-clean: stuck punctuation, whitespace.
        String text = preClean(raw);

        // 2. Bracket detected headers with sentinels so they become their
        //    own chunks even when jammed inline against content.
        text = HEADER_BRACKET.matcher(text).replaceAll(SENTINEL + "$1:" + SENTINEL);
        text = HEADER_BRACKET_CAPS.matcher(text).replaceAll(SENTINEL + "$1" + SENTINEL + "- ");

        // 3. Chunk + score per Rule 1.
        String[] chunks = CHUNK_BOUNDARY.split(text);
        StringBuilder html = new StringBuilder(text.length() + 128);
        List<String> contentRun = new ArrayList<>();
        for (String chunk : chunks) {
            String c = chunk.trim();
            if (c.isEmpty()) continue;
            if (scoreHeader(c) >= 2) {
                flushContent(html, contentRun);
                contentRun.clear();
                html.append("<h3>").append(escape(stripTrailingColon(c))).append("</h3>");
            } else {
                contentRun.add(c);
            }
        }
        flushContent(html, contentRun);

        // 4. Final sanitisation pass.
        return sanitise(html.toString());
    }

    // ───────────────────────────────────────────────────────────────
    //  Rule 1: header scoring
    // ───────────────────────────────────────────────────────────────

    /** Public for unit testing; returns the raw heuristic score 0..4. */
    public int scoreHeader(String chunk) {
        if (chunk == null) return 0;
        String s = chunk.trim();
        // Quick guards — list-shaped chunks can never be headers, even if
        // each item happens to be Title-Cased (e.g. "Java; Spring; Kafka").
        // We use 2 here (not 3 like the list-render rule) because two
        // semicolons already means three items — clearly content.
        if (countChar(s, ';') >= 2) return 0;
        if (HAS_BULLET.matcher(s).find()) return 0;
        int score = 0;

        // (a) Length window: typical headers are short but not single words
        int len = s.length();
        if (len >= 5 && len <= 60) score++;

        // (b) Uppercase-dominant or Title Case
        if (isMostlyUppercase(s) || isTitleCase(s)) score++;

        // (c) Trailing colon
        if (s.endsWith(":")) score++;

        // (d) Contains a known domain keyword (word-boundary matched)
        if (HEADER_KEYWORD_WORD.matcher(s).find()) score++;

        return score;
    }

    private static boolean isMostlyUppercase(String s) {
        int letters = 0, upper = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters > 0 && (upper * 100 / letters) > 50;
    }

    private static boolean isTitleCase(String s) {
        String stripped = s.endsWith(":") ? s.substring(0, s.length() - 1) : s;
        String[] words = stripped.split("\\s+");
        if (words.length < 2 || words.length > 8) return false;
        long titleWords = TITLE_CASE_WORD.matcher(stripped).results().count();
        // > 60% of words are Title-Case-shaped
        return words.length > 0 && (titleWords * 100 / words.length) >= 60;
    }

    private static String stripTrailingColon(String s) {
        return s.endsWith(":") ? s.substring(0, s.length() - 1).trim() : s;
    }

    // ───────────────────────────────────────────────────────────────
    //  Content emission — Rules 2 & 3
    // ───────────────────────────────────────────────────────────────

    private void flushContent(StringBuilder html, List<String> contentRun) {
        if (contentRun.isEmpty()) return;
        // Rejoin chunks with ". " so the bullet/sentence detectors can scan a single block.
        String block = String.join(". ", contentRun).trim();
        block = block.replace("..", ".");
        if (block.isEmpty()) return;

        if (HAS_BULLET.matcher(block).find()) {
            renderExplicitBulletList(html, block);
            return;
        }
        if (countChar(block, ';') >= 3) {
            renderSemicolonList(html, block);
            return;
        }
        if (block.length() > 300) {
            renderParagraphGroups(html, block);
            return;
        }
        html.append("<p>").append(escape(ensureTerminalPeriod(block))).append("</p>");
    }

    /** Rule 2(a) — split on explicit bullet glyphs. */
    private static void renderExplicitBulletList(StringBuilder html, String block) {
        String[] parts = BULLET_SPLIT.split(block);
        html.append("<ul>");
        for (String p : parts) {
            String item = stripLeadingBullet(p.trim());
            item = stripTrailingPeriod(item);
            if (item.isEmpty()) continue;
            html.append("<li>").append(escape(item)).append("</li>");
        }
        html.append("</ul>");
    }

    /** Leading bullet glyphs slip through when the very first item starts
     *  with one (e.g. "- 5+ years..." after the all-caps header was bracketed). */
    private static String stripLeadingBullet(String s) {
        return s.replaceFirst("^[-•●▪◦■□▶▸►✓✔➢➤*]\\s*", "");
    }

    /** Rule 2(b) — inline list using semicolons as separators. */
    private static void renderSemicolonList(StringBuilder html, String block) {
        String cleaned = block.endsWith(".") ? block.substring(0, block.length() - 1) : block;
        String[] parts = cleaned.split("\\s*;\\s*");
        html.append("<ul>");
        for (String p : parts) {
            String item = stripTrailingPeriod(p.trim());
            if (item.isEmpty()) continue;
            html.append("<li>").append(escape(item)).append("</li>");
        }
        html.append("</ul>");
    }

    /** Rule 3 — slice dense walls of text into 2–3 sentence paragraphs. */
    private static void renderParagraphGroups(StringBuilder html, String block) {
        String[] sentences = SENTENCE_SPLIT.split(block);
        StringBuilder group = new StringBuilder();
        int sentenceInGroup = 0;
        int targetGroupSize = 3;
        for (String s : sentences) {
            String sentence = s.trim();
            if (sentence.isEmpty()) continue;
            group.append(sentence);
            if (!sentence.endsWith(".")) group.append('.');
            group.append(' ');
            sentenceInGroup++;
            if (sentenceInGroup >= targetGroupSize) {
                html.append("<p>").append(escape(group.toString().trim())).append("</p>");
                group.setLength(0);
                sentenceInGroup = 0;
            }
        }
        if (group.length() > 0) {
            html.append("<p>").append(escape(group.toString().trim())).append("</p>");
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────────────────────

    private static String preClean(String raw) {
        String text = STUCK_PERIOD.matcher(raw).replaceAll(". $1");
        text = STUCK_COLON.matcher(text).replaceAll(": $1");
        text = WHITESPACE_RUNS.matcher(text).replaceAll(" ").trim();
        return text;
    }

    /** Rule 4 — final cleanup pass. */
    private static String sanitise(String html) {
        String out = html;
        out = EMPTY_LI.matcher(out).replaceAll("");
        out = ORPHAN_BR.matcher(out).replaceAll("");
        out = out.replace("<p></p>", "");
        out = out.replace("<ul></ul>", "");
        // Strip any leftover sentinel
        out = out.replace(SENTINEL, "");
        return out.trim();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int countChar(String s, char target) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == target) n++;
        return n;
    }

    private static String stripTrailingPeriod(String s) {
        return s.endsWith(".") ? s.substring(0, s.length() - 1).trim() : s;
    }

    private static String ensureTerminalPeriod(String s) {
        if (s.isEmpty()) return s;
        char last = s.charAt(s.length() - 1);
        return (last == '.' || last == '!' || last == '?') ? s : s + ".";
    }
}
