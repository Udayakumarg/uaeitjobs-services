package com.uaeitjobs.dto;

import com.uaeitjobs.entity.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AuthDTO {
    private AuthDTO() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()])[A-Za-z\\d@$!%*?&_\\-#^()]{8,}$",
                    message = "must contain uppercase, lowercase, digit, and a special character"
            )
            String password,
            @NotNull UserType userType,
            String phone,
            String country
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record UserResponse(Long id, String email, String displayName, UserType userType, String phone, String country, boolean verified, OffsetDateTime createdAt) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
    }
}
