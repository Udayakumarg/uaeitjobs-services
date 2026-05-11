package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;

public final class HRProfileDTO {
    private HRProfileDTO() {
    }

    public record Request(@NotBlank String companyName, String companyLogoUrl, String website, String industry, String subscriptionTier) {
    }

    public record Response(Long id, String companyName, String companyLogoUrl, String website, String industry, String subscriptionTier) {
    }
}
