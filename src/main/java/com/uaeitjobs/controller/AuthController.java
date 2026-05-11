package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDTO.UserResponse register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDTO.AuthResponse login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthDTO.AuthResponse refresh(@Valid @RequestBody AuthDTO.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthDTO.RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthDTO.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }
}
