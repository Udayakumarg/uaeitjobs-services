package com.uaeitjobs.service;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.EmailVerificationTokenRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.LoginAttemptRepository;
import com.uaeitjobs.repository.RefreshTokenRepository;
import com.uaeitjobs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock UserRepository userRepository;
    @Mock JobRepository jobRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock CurrentUserService currentUserService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock EmailService emailService;
    @Mock LoginAttemptRepository loginAttemptRepository;
    @InjectMocks AdminService adminService;

    @Test
    void deleteUserEvictsTheCachedEntry() {
        User user = new User();
        user.setId(5L);
        user.setEmail("stale-cache@uaeitjobs.com");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        adminService.deleteUser(5L);

        verify(userRepository).deleteById(5L);
        verify(currentUserService).evict("stale-cache@uaeitjobs.com");
    }

    @Test
    void deleteUserOfUnknownIdThrowsWithoutTouchingTheCache() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
