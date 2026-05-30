package com.uaeitjobs.service;

import com.uaeitjobs.repository.EmailVerificationTokenRepository;
import com.uaeitjobs.repository.PasswordResetTokenRepository;
import com.uaeitjobs.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Nightly maintenance job that purges stale authentication tokens.
 *
 * <p>Runs at 03:00 server time every day. Removes:
 * <ul>
 *   <li>Refresh tokens that have expired or been explicitly revoked.</li>
 *   <li>Email verification tokens that have been used or have expired.</li>
 * </ul>
 *
 * <p>Relies on {@code @EnableScheduling} being present on the application class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        int refreshDeleted = refreshTokenRepository.deleteExpiredOrRevoked(now);
        int verificationDeleted = emailVerificationTokenRepository.deleteExpiredOrUsed(now);
        int resetDeleted = passwordResetTokenRepository.deleteExpiredOrUsed(now);
        log.info("Token cleanup complete — refresh: {}, verification: {}, password-reset: {} removed",
                refreshDeleted, verificationDeleted, resetDeleted);
    }
}
