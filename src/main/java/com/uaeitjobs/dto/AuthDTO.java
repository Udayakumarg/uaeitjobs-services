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

    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()])[A-Za-z\\d@$!%*?&_\\-#^()]{8,}$",
                    message = "must contain uppercase, lowercase, digit, and a special character"
            )
            String newPassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#^()])[A-Za-z\\d@$!%*?&_\\-#^()]{8,}$",
                    message = "must contain uppercase, lowercase, digit, and a special character"
            )
            String newPassword
    ) {
    }

    public record UpdateUserRequest(
            @Size(max = 100)   String displayName,
            @Size(max = 30)    String phone,
            @Size(max = 100)   String country,
            /** Client-compressed base64 JPEG. Must start with "data:image/" if present.
             *  Max ~15 KB base64 string — enforced at 20 000 chars. */
            @Size(max = 20000) String avatarUrl
    ) {
    }

    public record UserResponse(Long id, String email, String displayName, UserType userType, String phone, String country, boolean verified, OffsetDateTime createdAt, String avatarUrl) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
    }
}
