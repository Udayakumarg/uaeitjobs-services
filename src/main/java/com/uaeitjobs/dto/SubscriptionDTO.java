package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public final class SubscriptionDTO {
    private SubscriptionDTO() {
    }

    public record UpgradeRequest(@NotBlank String tier) {
    }

    public record Response(Long id, String tier, int featuredJobsLimit, int featuredJobsUsed, int monthlyJobLimit, int jobsPosted, OffsetDateTime startedAt, OffsetDateTime expiresAt, boolean active) {
    }
}
