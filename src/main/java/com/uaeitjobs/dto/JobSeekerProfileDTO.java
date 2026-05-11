package com.uaeitjobs.dto;

public final class JobSeekerProfileDTO {
    private JobSeekerProfileDTO() {
    }

    public record Request(String cvUrl, String headline, String summary, Integer yearsExperience, String visaStatus, String skills, String experience, String education) {
    }

    public record Response(Long id, String cvUrl, String headline, String summary, Integer yearsExperience, String visaStatus, String skills, String experience, String education) {
    }

    public record SkillsRequest(String skills) {
    }
}
