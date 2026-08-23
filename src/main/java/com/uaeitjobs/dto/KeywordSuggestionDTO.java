package com.uaeitjobs.dto;

import java.util.List;

/** Safe DTO for keyword-suggestion responses — never exposes the raw JPA entity. */
public final class KeywordSuggestionDTO {
    private KeywordSuggestionDTO() {}

    public record Response(
            List<String> added,
            List<String> skippedExisting,
            List<String> skippedInvalid
    ) {}
}
