package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.service.AuthService;
import com.uaeitjobs.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated endpoints that are shared across all user types (job_seeker, hr, admin).
 * Handles mutations to the core User entity (display name, phone, country).
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    /**
     * Update the current user's display name, phone, country, and/or avatar.
     * Only fields present in the request body are mutated (null = keep existing).
     */
    @PatchMapping("/profile")
    public AuthDTO.UserResponse updateProfile(@Valid @RequestBody AuthDTO.UpdateUserRequest request) {
        return authService.updateProfile(currentUserService.get(), request);
    }

    /**
     * Change the current user's password.
     * Requires the correct current password. Revokes all other refresh tokens
     * on success, signing out any other active sessions.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDTO.ChangePasswordRequest request) {
        authService.changePassword(currentUserService.get(), request);
    }
}
