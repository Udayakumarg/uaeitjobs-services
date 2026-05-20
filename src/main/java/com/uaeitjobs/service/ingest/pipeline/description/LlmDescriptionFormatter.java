package com.uaeitjobs.service.ingest.pipeline.description;

import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmClient;
import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LLM-backed description formatter.  Replaces the heuristic regex engine
 * for sources where context is needed to identify section boundaries —
 * JSearch in particular emits one flat string with headers jammed inline,
 * which the heuristic couldn't reliably split.
 *
 * <h2>Contract</h2>
 * Registered with vendor {@code "default"} so the
 * {@link DescriptionFormatterRegistry} routes every unknown source through
 * it.  When LLM access is disabled, the API key is missing, the call
 * times out, the response is malformed, or the LLM hallucinates non-HTML,
 * the formatter <strong>silently falls back to the heuristic formatter</strong>
 * so no job is ever dropped from the pipeline.
 *
 * <h2>Observability</h2>
 * Every call logs INFO with elapsed time and outcome; WARN on fallback
 * with the failure reason.  Set
 * {@code logging.level.com.uaeitjobs.service.ingest.pipeline.description=DEBUG}
 * to also see request/response sizes.
 */
@Slf4j
@Component
public class LlmDescriptionFormatter implements JobDescriptionFormatter {

    /** System prompt — kept here as a constant so it ships in version control alongside the code. */
    private static final String SYSTEM_PROMPT = """
            You are an HTML formatter for job postings.

            INPUT: a flat block of text. HTML tags and newlines have already been stripped\
             by an aggregator, leaving section headers ("Responsibilities", "Required Skills"\
             etc.) inline against the previous sentence.

            TASK: add semantic structure using ONLY these HTML tags: <h3>, <p>, <ul>, <li>.

            RULES (must follow exactly):
            1. Wrap section headers in <h3>. Strip any trailing colon from the heading itself.
            2. Lists of skills, responsibilities, or qualifications become <ul><li>…</li></ul>.
            3. Prose paragraphs become <p>…</p>.
            4. PRESERVE the original wording exactly. Do not paraphrase, summarise, translate,\
             or add new content.
            5. Do NOT include any other HTML tags (no <br>, no <div>, no <strong>, no inline styles).
            6. Do NOT include attributes, classes, IDs, or inline CSS.
            7. Do NOT wrap the output in markdown code fences. No ``` or ```html.
            8. Do NOT add explanations, commentary, headings of your own, or sign-offs.
            9. Return ONLY the raw HTML, starting with the first '<' character.
            """;

    /** Whitelist for sanitisation. */
    private static final Pattern ALLOWED_TAG =
            Pattern.compile("^</?(h3|p|ul|li)>$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_TAG =
            Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```(?:html)?\\s*|\\s*```\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    /** Strip dangerous block tags <strong>with their content</strong> — no script payload survives. */
    private static final Pattern DANGEROUS_BLOCK = Pattern.compile(
            "(?is)<(script|style|iframe|object|embed|svg)\\b[^>]*>.*?</\\1>");
    /** Strip dangerous self-closing or void tags. */
    private static final Pattern DANGEROUS_VOID = Pattern.compile(
            "(?is)<(link|meta|base|frame|input|button)\\b[^>]*/?>");

    private final LlmConfig config;
    private final HeuristicDescriptionFormatter heuristic;
    private final Map<String, LlmClient> clientsByName;
    /** Last LLM call timestamp — used for the optional rate-limit throttle. */
    private volatile long lastLlmCallAt = 0L;

    public LlmDescriptionFormatter(LlmConfig config,
                                   HeuristicDescriptionFormatter heuristic,
                                   List<LlmClient> clients) {
        this.config = config;
        this.heuristic = heuristic;
        this.clientsByName = clients.stream()
                .collect(Collectors.toUnmodifiableMap(c -> c.name().toLowerCase(), c -> c));
        if (config.enabled()) {
            log.info("LLM description formatter enabled — provider={}, timeoutMs={}, maxInputChars={}",
                    config.provider(), config.timeoutMs(), config.maxInputChars());
            if (clientsByName.get(config.provider().toLowerCase()) == null) {
                log.warn("LLM enabled but no client registered for provider '{}' — falling back to heuristic for all jobs",
                        config.provider());
            }
            if (config.apiKey() == null || config.apiKey().isBlank()) {
                log.warn("LLM enabled but app.llm.api-key is blank — every call will fall back to heuristic");
            }
        } else {
            log.info("LLM description formatter disabled — heuristic will run for every job");
        }
    }

    @Override
    public String vendor() {
        // This formatter is the new default. The heuristic formatter now
        // registers under "heuristic" so it can still be invoked explicitly.
        return DescriptionFormatterRegistry.DEFAULT_VENDOR;
    }

    @Override
    public String toHtml(String raw) {
        if (raw == null || raw.isBlank()) return "";
        if (!shouldCallLlm()) return heuristic.toHtml(raw);

        String input = truncateForBudget(raw);
        LlmClient client = clientsByName.get(config.provider().toLowerCase());
        throttleIfConfigured();
        long start = System.currentTimeMillis();
        try {
            String response = client.complete(SYSTEM_PROMPT, input);
            long elapsed = System.currentTimeMillis() - start;
            String html = sanitise(response);
            if (!looksLikeHtml(html)) {
                log.warn("LLM [{}] returned non-HTML ({} chars in {}ms) — falling back to heuristic. Preview: {}",
                        client.name(), response.length(), elapsed, preview(response));
                return heuristic.toHtml(raw);
            }
            log.info("LLM [{}] formatted job description in {}ms ({} chars in → {} chars HTML)",
                    client.name(), elapsed, input.length(), html.length());
            return html;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            // Walk the cause chain so the underlying IOException / Jackson error
            // shows up in the log instead of only the wrapper message.
            StringBuilder chain = new StringBuilder(e.getClass().getSimpleName())
                    .append(": ").append(e.getMessage());
            Throwable cause = e.getCause();
            int depth = 0;
            while (cause != null && depth < 3) {
                chain.append(" ← ").append(cause.getClass().getSimpleName())
                     .append(": ").append(cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            log.warn("LLM [{}] failed after {}ms ({}) — falling back to heuristic",
                    client.name(), elapsed, chain);
            return heuristic.toHtml(raw);
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Internals
    // ───────────────────────────────────────────────────────────────

    /**
     * Optional per-call rate limiter. When {@code app.llm.min-interval-ms} is
     * non-zero, we sleep before each call so the upstream rate limit is
     * respected. For Gemini free tier set this to 13000 (5 RPM); for paid
     * OpenAI Tier 1+ (500 RPM) leave it at 0.
     */
    private synchronized void throttleIfConfigured() {
        long min = config.minIntervalMs();
        if (min <= 0) return;
        long since = System.currentTimeMillis() - lastLlmCallAt;
        if (since < min) {
            long wait = min - since;
            try {
                log.debug("LLM throttle: sleeping {}ms to respect rate limit", wait);
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastLlmCallAt = System.currentTimeMillis();
    }

    private boolean shouldCallLlm() {
        if (!config.enabled()) return false;
        if (config.apiKey() == null || config.apiKey().isBlank()) return false;
        return clientsByName.get(config.provider().toLowerCase()) != null;
    }

    /** Cap input length so a single rogue posting can't burn through tokens. */
    private String truncateForBudget(String raw) {
        int cap = config.maxInputChars();
        if (raw.length() <= cap) return raw;
        log.debug("Truncating LLM input from {} → {} chars", raw.length(), cap);
        return raw.substring(0, cap);
    }

    /**
     * Sanitise the LLM's output:
     *   1. strip markdown code-fence wrappers (the LLM occasionally adds them despite instructions);
     *   2. drop any tag not in the whitelist;
     *   3. collapse excess whitespace.
     * User-visible text content is left intact — escaping happens server-side
     * before persistence is the LLM's responsibility (we double-check below).
     */
    private static String sanitise(String raw) {
        String s = CODE_FENCE.matcher(raw).replaceAll("").trim();
        // Strip dangerous tag blocks WITH their content so payloads don't leak.
        s = DANGEROUS_BLOCK.matcher(s).replaceAll("");
        s = DANGEROUS_VOID.matcher(s).replaceAll("");

        // Whitelist-only tag stripper
        Matcher m = ANY_TAG.matcher(s);
        StringBuilder out = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String tag = m.group();
            if (ALLOWED_TAG.matcher(tag).matches()) {
                out.append(tag.toLowerCase());
            }
            last = m.end();
        }
        out.append(s.substring(last));

        // Strip empty li/p/ul wrappers introduced by the strip pass
        String cleaned = out.toString()
                .replaceAll("<li>\\s*</li>", "")
                .replaceAll("<p>\\s*</p>", "")
                .replaceAll("<ul>\\s*</ul>", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned;
    }

    /** Reject responses that didn't actually emit HTML (e.g. an explanation paragraph). */
    private static boolean looksLikeHtml(String s) {
        if (s == null || s.isBlank()) return false;
        String trimmed = s.trim();
        // Must start with an allowed opening tag.
        return trimmed.matches("(?is)^<(h3|p|ul)>.*");
    }

    private static String preview(String s) {
        if (s == null) return "<null>";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "…";
    }
}
