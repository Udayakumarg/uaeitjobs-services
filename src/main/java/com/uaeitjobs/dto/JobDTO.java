package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class JobDTO {
    private JobDTO() {
    }

    public record JobRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String companyName,
            @NotBlank String description,
            String requirements,
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            String jobType,
            String experienceLevel,
            String locationUae,
            String skills,
            String linkedinUrl,
            Boolean featured,
            OffsetDateTime expiresAt,
            String visaType,
            String emirate,
            Boolean immediateJoiner,
            Boolean remoteUae,
            String jobCategory,
            String applyUrl
    ) {
    }

    public record JobResponse(
            Long id,
            String slug,
            String title,
            String companyName,
            String description,
            String requirements,
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            String jobType,
            String experienceLevel,
            String locationUae,
            String skills,
            String linkedinUrl,
            String source,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime expiresAt,
            boolean featured,
            boolean active,
            long viewCount,
            String visaType,
            String emirate,
            boolean immediateJoiner,
            boolean remoteUae,
            String jobCategory,
            String applyUrl,
            String descriptionSections,
            String descriptionHtml,
            String companyLogoUrl
    ) {
    }
}
