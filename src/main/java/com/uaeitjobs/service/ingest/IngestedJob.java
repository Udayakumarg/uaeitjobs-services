package com.uaeitjobs.service.ingest;

/**
 * Source-agnostic representation of a job pulled from an external feed.
 * Each ingest source maps its API response into this DTO; the orchestrator
 * is the only thing that touches the Job entity, so sources stay simple.
 */
public record IngestedJob(
        String externalId,
        String source,         // "adzuna" | "remoteok" | "wwr" ...
        String title,
        String companyName,
        String description,
        String requirements,
        String locationUae,
        String emirate,        // optional, lowercase enum value or null
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String jobType,
        String experienceLevel,
        String applyUrl,
        boolean remoteUae
) {
}
