package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SeekerAiDTO {
    private SeekerAiDTO() {
    }

    public record SettingsRequest(
            @NotBlank(message = "Provider is required") String provider,
            @NotBlank(message = "API key is required") String apiKey
    ) {
    }

    /** {@code maskedKey} is null when no key is configured; otherwise a preview like "sk-...ab12". */
    public record SettingsResponse(String provider, boolean configured, String maskedKey) {
    }

    public record DraftRequest(@NotNull(message = "jobId is required") Long jobId) {
    }

    public record DraftResponse(String coverLetter) {
    }
}
