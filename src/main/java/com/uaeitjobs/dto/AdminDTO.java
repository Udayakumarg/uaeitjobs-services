package com.uaeitjobs.dto;

import java.util.List;
import java.util.Map;

public final class AdminDTO {
    private AdminDTO() {
    }

    public record StatsResponse(long users, long jobSeekers, long hrUsers, long jobs, long applications, long revenueEstimateAed) {
    }

    // ── Login health ──────────────────────────────────────────────────────────

    /**
     * Today's login health snapshot: raw counts and a breakdown of failure reasons.
     * {@code failureBreakdown} maps LoginFailureReason name → count.
     */
    public record LoginHealthToday(
            long attempts,
            long successes,
            long failures,
            double successRate,
            Map<String, Long> failureBreakdown
    ) {
    }

    // ── Friction signals ──────────────────────────────────────────────────────

    /**
     * A single proactively-detected friction signal for one user.
     * {@code signalType} is one of: EMPLOYER_INACTIVE, VERIFIED_NEVER_LOGIN,
     * STUCK_PENDING_LONG, REPEATED_FAILURES.
     * {@code severity} is one of: HIGH, MEDIUM, LOW, INFO.
     * {@code suggestedAction} is one of: SEND_WELCOME, RESEND_VERIFICATION,
     * RESET_PASSWORD, REVIEW.
     */
    public record FrictionSignal(
            long userId,
            String email,
            String userType,
            String signalType,
            String severity,
            String message,
            long daysSinceCreated,
            long failedLoginAttempts24h,
            String suggestedAction
    ) {
    }

    // ── User activity ─────────────────────────────────────────────────────────

    public record UserActivityResponse(
            long totalUsers,
            long verifiedUsers,
            long pendingUsers,
            long activeToday,
            long activeLast7Days,
            long activeLast30Days,
            long neverLoggedIn,
            long newLast7Days,
            long newLast30Days,
            long activeSessions,
            List<UserRow> stuckPending,
            List<UserRow> recentSignups,
            List<UserRow> neverReturned,
            List<CountryCount> topCountries,
            LoginHealthToday loginHealthToday
    ) {
    }

    public record UserRow(long id, String email, String userType, boolean verified, String createdAt, String lastLogin) {
    }

    public record CountryCount(String country, long count) {
    }
}
