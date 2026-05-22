package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.exception.UnauthorizedException;
import com.uaeitjobs.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.jwt.refresh-token-days}")
    private long refreshTokenDays;

    /** Set {@code false} in dev so the cookie works on plain HTTP localhost. */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    /** Use {@code Lax} in dev when frontend and backend are on different ports. */
    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    // ── Register ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDTO.UserResponse register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        return authService.register(request);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public AuthDTO.AuthResponse login(@Valid @RequestBody AuthDTO.LoginRequest request,
                                      HttpServletResponse response) {
        AuthDTO.AuthResponse result = authService.login(request);
        setRefreshCookie(response, result.refreshToken());
        // Return access token + user; omit refresh token from body (now in cookie)
        return new AuthDTO.AuthResponse(result.accessToken(), null, result.user());
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    /**
     * Reads the refresh token from the {@code refresh_token} httpOnly cookie.
     * Falls back to the request body for clients that still hold a token from
     * the pre-cookie era (one-time migration path — body support will be
     * removed once all sessions have cycled through a cookie-based refresh).
     */
    @PostMapping("/refresh")
    public AuthDTO.AuthResponse refresh(
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) AuthDTO.RefreshRequest body,
            HttpServletResponse response) {

        String token = cookieToken != null ? cookieToken
                : (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()
                        ? body.refreshToken() : null);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Refresh token required");
        }
        AuthDTO.AuthResponse result = authService.refresh(token);
        setRefreshCookie(response, result.refreshToken());
        return new AuthDTO.AuthResponse(result.accessToken(), null, result.user());
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) AuthDTO.RefreshRequest body,
            HttpServletResponse response) {

        String token = cookieToken != null ? cookieToken
                : (body != null ? body.refreshToken() : null);
        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
    }

    // ── Email verification ────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthDTO.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTokenDays))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
