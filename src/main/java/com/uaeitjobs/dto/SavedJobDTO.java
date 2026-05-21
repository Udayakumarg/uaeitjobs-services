package com.uaeitjobs.dto;

import java.time.OffsetDateTime;

/** Safe DTO for saved-job responses — never exposes raw JPA entities or User data. */
public final class SavedJobDTO {
    private SavedJobDTO() {}

    public record Response(
            Long id,
            JobDTO.JobResponse job,
            OffsetDateTime savedAt
    ) {}
}
