package com.uaeitjobs.dto;

import java.util.List;

public final class AdminDTO {
    private AdminDTO() {
    }

    public record StatsResponse(long users, long jobSeekers, long hrUsers, long jobs, long applications, long revenueEstimateAed) {
    }

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
            List<CountryCount> topCountries
    ) {
    }

    public record UserRow(long id, String email, String userType, boolean verified, String createdAt, String lastLogin) {
    }

    public record CountryCount(String country, long count) {
    }
}
