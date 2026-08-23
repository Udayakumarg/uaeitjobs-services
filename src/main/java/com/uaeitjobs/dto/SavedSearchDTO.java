package com.uaeitjobs.dto;

import java.time.OffsetDateTime;

/** Safe DTO for saved-search responses — never exposes the raw JPA entity or User data. */
public final class SavedSearchDTO {
    private SavedSearchDTO() {}

    public record Request(String name, String filters) {}

    public record Response(
            Long id,
            String name,
            String filters,
            OffsetDateTime createdAt
    ) {}
}
