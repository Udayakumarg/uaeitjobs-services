package com.uaeitjobs.service.ingest;

import java.time.OffsetDateTime;

/**
 * Source-agnostic representation of a job pulled from an external feed.
 * Each ingest source maps its API response into this DTO; the pipeline is
 * the only thing that touches the Job entity.
 */
public record IngestedJob(
        String externalJobId,  // source-stable ID (jsearch job_id, adzuna id, …)
        String source,         // "jsearch" | "adzuna" | "remoteok" | …
        String publisher,      // who originally posted (LinkedIn, Bayt, …)
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
        boolean remoteUae,
        OffsetDateTime postedAt  // original posting date from the source API; null when unavailable
) {
}
