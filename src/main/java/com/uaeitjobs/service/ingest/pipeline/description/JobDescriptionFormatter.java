package com.uaeitjobs.service.ingest.pipeline.description;

/**
 * Vendor-aware description formatter contract.
 *
 * Each registered implementation handles one upstream source by name.
 * The {@link DescriptionFormatterRegistry} resolves the right
 * implementation for an incoming job; if no specific implementation
 * matches, the heuristic fallback runs.
 *
 * Implementations MUST be safe to call from any thread and MUST NOT
 * mutate the input string. They MUST produce sanitised, attribute-free
 * HTML using only the following tag whitelist:
 *   &lt;h3&gt; &lt;p&gt; &lt;ul&gt; &lt;li&gt; &lt;strong&gt;
 *
 * Implementations should target sub-5ms execution per job description
 * so the ingestion pipeline can process tens of thousands of postings
 * without becoming a bottleneck.
 */
public interface JobDescriptionFormatter {

    /**
     * Stable identifier matching the value in {@code jobs.source}.
     * The reserved value {@code "default"} marks the heuristic fallback.
     */
    String vendor();

    /**
     * Produce clean, sanitised HTML from a raw description payload.
     * Must never throw — return {@code ""} for null or empty input.
     */
    String toHtml(String raw);
}
