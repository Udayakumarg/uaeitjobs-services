package com.uaeitjobs.dto;

import com.uaeitjobs.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class ApplicationDTO {
    private ApplicationDTO() {
    }

    public record Request(@NotNull Long jobId, String coverLetter) {
    }

    public record StatusRequest(@NotNull ApplicationStatus status) {
    }

    public record Response(Long id, JobDTO.JobResponse job, AuthDTO.UserResponse applicant, String coverLetter, OffsetDateTime appliedAt, ApplicationStatus status) {
    }
}
