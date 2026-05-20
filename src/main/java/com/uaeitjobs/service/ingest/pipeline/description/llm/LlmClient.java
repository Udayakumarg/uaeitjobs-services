package com.uaeitjobs.service.ingest.pipeline.description.llm;

/**
 * Provider-agnostic completion interface.  Implementations are Spring
 * components registered with {@link #name()} matching the value of
 * {@code app.llm.provider}.
 */
public interface LlmClient {

    /** Identifier used to pick this client from configuration. e.g. "gemini" / "claude". */
    String name();

    /**
     * Perform a single synchronous completion call.
     *
     * @param systemPrompt the instruction prompt (role, format requirements)
     * @param userPrompt   the actual content the LLM should transform
     * @return the assistant's text response (the formatter strips any wrappers afterward)
     * @throws Exception on network failure, HTTP error, or upstream timeout
     */
    String complete(String systemPrompt, String userPrompt) throws Exception;
}
