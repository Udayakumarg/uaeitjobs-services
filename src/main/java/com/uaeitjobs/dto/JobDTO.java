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
            OffsetDateTime postedAt,
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
        /**
         * Returns a copy of this response with {@code applyUrl} and
         * {@code linkedinUrl} set to {@code null}.  Used to gate recruiter
         * contact links for anonymous (unauthenticated) callers so that
         * direct-apply URLs are only visible to registered users.
         */
        public JobResponse withMaskedApply() {
            return new JobResponse(id, slug, title, companyName, description, requirements,
                    salaryMin, salaryMax, salaryCurrency, jobType, experienceLevel, locationUae,
                    skills, null /* linkedinUrl */, source, postedAt, createdAt, updatedAt, expiresAt,
                    featured, active, viewCount, visaType, emirate, immediateJoiner, remoteUae,
                    jobCategory, null /* applyUrl */, descriptionSections, descriptionHtml, companyLogoUrl);
        }
    }
}
