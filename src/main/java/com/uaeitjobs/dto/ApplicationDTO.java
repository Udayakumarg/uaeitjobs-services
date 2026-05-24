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

    /**
     * Enriched view returned to HR users via {@code GET /hr/jobs/:id/applicants}.
     * Includes the applicant's {@link com.uaeitjobs.entity.JobSeekerProfile} fields
     * (all nullable — the seeker may not have filled in their profile yet).
     */
    public record HrView(Long id, JobDTO.JobResponse job, AuthDTO.UserResponse applicant, String coverLetter,
                         OffsetDateTime appliedAt, ApplicationStatus status,
                         String headline, Integer yearsExperience, String skills, String visaStatus, String cvUrl) {
    }
}
