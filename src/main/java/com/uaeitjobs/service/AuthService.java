package com.uaeitjobs.service;

import com.uaeitjobs.config.JwtTokenProvider;
import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.*;
import com.uaeitjobs.exception.UnauthorizedException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.UserMapper;
import com.uaeitjobs.entity.PasswordResetToken;
import com.uaeitjobs.repository.EmailVerificationTokenRepository;
import com.uaeitjobs.repository.PasswordResetTokenRepository;
import com.uaeitjobs.repository.RefreshTokenRepository;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUserService currentUserService;

    @Value("${app.jwt.refresh-token-days}")
    private long refreshTokenDays;

    @Transactional
    public AuthDTO.UserResponse register(AuthDTO.RegisterRequest request) {
        if (request.userType() == UserType.admin) {
            throw new ValidationException("Admin users cannot self-register");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ValidationException("Email is already registered");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUserType(request.userType());
        user.setPhone(request.phone());
        user.setCountry(request.country());
        user.setVerified(false);
        user = userRepository.save(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(OffsetDateTime.now().plusDays(1));
        emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
        return userMapper.toResponse(user);
    }

    @Transactional
    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Record before throwing — REQUIRES_NEW in loginAttemptService ensures
            // this commit survives even though our transaction is about to throw.
            loginAttemptService.record(null, email, false, LoginFailureReason.USER_NOT_FOUND);
            throw new UnauthorizedException("Invalid credentials");
        }
        User user = userOpt.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.record(user, email, false, LoginFailureReason.INVALID_PASSWORD);
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!user.isVerified()) {
            loginAttemptService.record(user, email, false, LoginFailureReason.EMAIL_NOT_VERIFIED);
            throw new UnauthorizedException("Please verify your email address before logging in");
        }

        user.setLastLogin(OffsetDateTime.now());
        loginAttemptService.record(user, email, true, null);
        return issueTokens(user);
    }

    @Transactional
    public AuthDTO.AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            // Reuse of an already-rotated-away token is the signature of theft —
            // an attacker and the legitimate user both hold a copy, and one of
            // them redeemed it first. Revoke the whole family so the other
            // copy (wherever it is) stops working too, not just this one.
            refreshTokenRepository.revokeAllByUser(refreshToken.getUser());
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Token rotation: revoke the consumed token before issuing a new one.
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenValue)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> new ValidationException("Invalid verification token"));
        token.setUsed(true);
        token.getUser().setVerified(true);
    }

    /**
     * Initiates a password reset flow. Always returns successfully, even when the
     * email is not registered, to prevent user enumeration attacks.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setToken(UUID.randomUUID().toString());
            token.setExpiresAt(OffsetDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(token);
            emailService.sendPasswordResetEmail(user.getEmail(), token.getToken());
        });
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenValue)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> new ValidationException("This reset link is invalid or has expired. Please request a new one."));
        token.setUsed(true);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // A password reset means the user suspects compromise (or lost access) —
        // revoke all existing sessions the same way changePassword does, so a
        // stolen refresh token doesn't stay valid after the reset.
        refreshTokenRepository.revokeAllByUser(user);
    }

    /**
     * Changes the current user's password after verifying the existing one.
     * All existing refresh tokens are revoked so other devices are logged out.
     */
    @Transactional
    public void changePassword(User currentUser, AuthDTO.ChangePasswordRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Revoke all refresh tokens — logs out all other active sessions
        refreshTokenRepository.revokeAllByUser(user);
    }

    /**
     * Updates the current user's display name, phone, and country.
     * Evicts the user from the {@code CurrentUserService} cache so subsequent
     * requests within the same cache window see the fresh values.
     */
    @Transactional
    public AuthDTO.UserResponse updateProfile(User currentUser, AuthDTO.UpdateUserRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.country() != null) {
            user.setCountry(request.country().isBlank() ? null : request.country().trim());
        }
        if (request.avatarUrl() != null) {
            String avatar = request.avatarUrl().trim();
            if (avatar.isBlank()) {
                user.setAvatarUrl(null);
            } else if (avatar.startsWith("data:image/")) {
                user.setAvatarUrl(avatar);
            }
            // silently ignore invalid values that don't start with "data:image/"
        }
        currentUserService.evict(user.getEmail());
        return userMapper.toResponse(user);
    }

    private AuthDTO.AuthResponse issueTokens(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenDays));
        refreshTokenRepository.save(refreshToken);
        return new AuthDTO.AuthResponse(jwtTokenProvider.generateAccessToken(user), refreshToken.getToken(), userMapper.toResponse(user));
    }
}
