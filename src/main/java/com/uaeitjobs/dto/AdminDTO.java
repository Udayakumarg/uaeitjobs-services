package com.uaeitjobs.dto;

public final class AdminDTO {
    private AdminDTO() {
    }

    public record StatsResponse(long users, long jobSeekers, long hrUsers, long jobs, long applications, long revenueEstimateAed) {
    }
}
