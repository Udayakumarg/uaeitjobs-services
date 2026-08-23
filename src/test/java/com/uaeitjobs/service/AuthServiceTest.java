package com.uaeitjobs.service;

import com.uaeitjobs.config.JwtTokenProvider;
import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.PasswordResetToken;
import com.uaeitjobs.entity.RefreshToken;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.UnauthorizedException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.UserMapper;
import com.uaeitjobs.repository.EmailVerificationTokenRepository;
import com.uaeitjobs.repository.PasswordResetTokenRepository;
import com.uaeitjobs.repository.RefreshTokenRepository;
import com.uaeitjobs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserMapper userMapper;
    @Mock EmailService emailService;
    @Mock CurrentUserService currentUserService;
    @InjectMocks AuthService authService;

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("hr@demo.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new AuthDTO.RegisterRequest("hr@demo.com", "password123", UserType.hr, null, "UAE")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email is already registered");
    }

    @Test
    void registerCreatesVerificationToken() {
        ReflectionTestUtils.setField(authService, "refreshTokenDays", 7L);
        when(userRepository.existsByEmailIgnoreCase("hr@demo.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        authService.register(new AuthDTO.RegisterRequest("hr@demo.com", "password123", UserType.hr, null, "UAE"));

        verify(emailVerificationTokenRepository).save(any());
        verify(emailService).sendVerificationEmail(eq("hr@demo.com"), any());
    }

    @Test
    void resetPasswordRevokesAllRefreshTokens() {
        User user = new User();
        user.setId(42L);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

        authService.resetPassword("valid-token", "newPassword123");

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void refreshOfARevokedTokenRevokesTheWholeFamily() {
        User user = new User();
        user.setId(7L);
        RefreshToken revoked = new RefreshToken();
        revoked.setUser(user);
        revoked.setRevoked(true);
        revoked.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByToken("stolen-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }
}
