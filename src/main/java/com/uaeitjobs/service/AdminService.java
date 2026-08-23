package com.uaeitjobs.service;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.entity.EmailVerificationToken;
import com.uaeitjobs.entity.LoginFailureReason;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.EmailVerificationTokenRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.LoginAttemptRepository;
import com.uaeitjobs.repository.RefreshTokenRepository;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final LoginAttemptRepository loginAttemptRepository;

    public AdminDTO.StatsResponse stats() {
        long hr = userRepository.countByUserType(UserType.hr);
        return new AdminDTO.StatsResponse(userRepository.count(), userRepository.countByUserType(UserType.job_seeker), hr, jobRepository.count(), applicationRepository.count(), hr * 499);
    }

    public AdminDTO.UserActivityResponse userActivity() {
        OffsetDateTime now            = OffsetDateTime.now();
        OffsetDateTime oneDayAgo     = now.minusDays(1);
        OffsetDateTime sevenDaysAgo  = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        long totalUsers       = userRepository.count();
        long verifiedUsers    = userRepository.countByVerifiedTrue();
        long pendingUsers     = userRepository.countByVerifiedFalse();
        long activeToday      = userRepository.countByLastLoginAfter(oneDayAgo);
        long activeLast7Days  = userRepository.countByLastLoginAfter(sevenDaysAgo);
        long activeLast30Days = userRepository.countByLastLoginAfter(thirtyDaysAgo);
        long neverLoggedIn    = userRepository.countByLastLoginIsNull();
        long newLast7Days     = userRepository.countByCreatedAtAfter(sevenDaysAgo);
        long newLast30Days    = userRepository.countByCreatedAtAfter(thirtyDaysAgo);
        long activeSessions   = refreshTokenRepository.countByRevokedFalseAndExpiresAtAfter(now);

        List<AdminDTO.UserRow> stuckPending = toRows(
                userRepository.findTop20ByVerifiedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(oneDayAgo));
        List<AdminDTO.UserRow> recentSignups = toRows(
                userRepository.findTop20ByOrderByCreatedAtDesc());
        List<AdminDTO.UserRow> neverReturned = toRows(
                userRepository.findTop20ByVerifiedTrueAndLastLoginIsNullOrderByCreatedAtDesc());

        List<AdminDTO.CountryCount> topCountries = userRepository
                .topCountries(PageRequest.of(0, 8)).stream()
                .map(c -> new AdminDTO.CountryCount(c.getCountry(), c.getTotal()))
                .collect(Collectors.toList());

        // ── Login health for today (midnight UTC → now) ───────────────────────
        OffsetDateTime todayMidnight = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        long attempts   = loginAttemptRepository.countByCreatedAtAfter(todayMidnight);
        long successes  = loginAttemptRepository.countBySuccessAndCreatedAtAfter(true, todayMidnight);
        long failures   = loginAttemptRepository.countBySuccessAndCreatedAtAfter(false, todayMidnight);
        double rawRate  = attempts == 0 ? 0.0 : (double) successes / attempts * 100.0;
        double successRate = Math.round(rawRate * 10.0) / 10.0;

        List<Object[]> breakdownRows = loginAttemptRepository.failureBreakdownSince(todayMidnight);
        Map<String, Long> failureBreakdown = new LinkedHashMap<>();
        for (Object[] row : breakdownRows) {
            LoginFailureReason reason = (LoginFailureReason) row[0];
            Long count = (Long) row[1];
            failureBreakdown.put(reason.name(), count);
        }

        AdminDTO.LoginHealthToday loginHealthToday = new AdminDTO.LoginHealthToday(
                attempts, successes, failures, successRate, failureBreakdown);

        return new AdminDTO.UserActivityResponse(
                totalUsers, verifiedUsers, pendingUsers,
                activeToday, activeLast7Days, activeLast30Days,
                neverLoggedIn, newLast7Days, newLast30Days,
                activeSessions, stuckPending, recentSignups, neverReturned,
                topCountries, loginHealthToday);
    }

    private List<AdminDTO.UserRow> toRows(List<User> users) {
        return users.stream().map(u -> new AdminDTO.UserRow(
                u.getId(), u.getEmail(), u.getUserType().name(), u.isVerified(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null,
                u.getLastLogin()  != null ? u.getLastLogin().toString()  : null
        )).collect(Collectors.toList());
    }

    public Page<?> users(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByEmailContainingIgnoreCase(search, pageable);
    }

    @Transactional
    public void setJobActive(Long id, boolean active) {
        jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found")).setActive(active);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String email = user.getEmail();
        userRepository.deleteById(id);
        // Otherwise the 2-minute Caffeine cache in CurrentUserService keeps
        // serving the now-deleted user for the rest of the cache window.
        currentUserService.evict(email);
    }

    /**
     * Resend the account-activation email for a user who hasn't verified yet.
     * Admins can use this to unblock users stuck on "Pending" status.
     */
    @Transactional
    public void resendVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isVerified()) {
            throw new ValidationException("This account is already verified");
        }
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(OffsetDateTime.now().plusDays(1));
        emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
    }

    /** Create any user type (including admin) — admin-created accounts are pre-verified. */
    @Transactional
    public User createUser(String email, String password, UserType userType) {
        if (email == null || email.isBlank()) throw new ValidationException("Email is required");
        if (password == null || password.length() < 8) throw new ValidationException("Password must be at least 8 characters");
        if (userRepository.existsByEmailIgnoreCase(email)) throw new ValidationException("Email is already registered");
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUserType(userType);
        user.setVerified(true); // admin-created accounts skip email verification
        return userRepository.save(user);
    }

    /**
     * Scans user data and login-attempt history to build a prioritised list of
     * friction signals — accounts that may need a nudge or admin intervention.
     *
     * Signal types:
     *   REPEATED_FAILURES    — ≥3 failed logins in the past 24 h
     *   EMPLOYER_INACTIVE    — HR account verified but never signed in (>3 days)
     *   VERIFIED_NEVER_LOGIN — Job-seeker verified but never signed in (>3 days)
     *   STUCK_PENDING_LONG   — Unverified for >48 h
     */
    public List<AdminDTO.FrictionSignal> frictionSignals() {
        OffsetDateTime now          = OffsetDateTime.now();
        OffsetDateTime oneDayAgo    = now.minusDays(1);
        OffsetDateTime twoDaysAgo   = now.minusDays(2);
        OffsetDateTime threeDaysAgo = now.minusDays(3);

        List<AdminDTO.FrictionSignal> signals = new ArrayList<>();

        // ── REPEATED_FAILURES ─────────────────────────────────────────────────
        List<Object[]> repeatedRows = loginAttemptRepository.usersWithRepeatedFailuresSince(oneDayAgo, 3L);
        for (Object[] row : repeatedRows) {
            Long userId = (Long) row[0];
            Long count  = (Long) row[1];
            userRepository.findById(userId).ifPresent(user -> {
                String severity  = count >= 5 ? "HIGH" : "MEDIUM";
                long daysSince   = ChronoUnit.DAYS.between(user.getCreatedAt(), now);
                signals.add(new AdminDTO.FrictionSignal(
                        user.getId(), user.getEmail(), user.getUserType().name(),
                        "REPEATED_FAILURES", severity,
                        count + " failed login attempts in the last 24 hours",
                        daysSince, count, "RESET_PASSWORD"));
            });
        }

        // ── EMPLOYER_INACTIVE + VERIFIED_NEVER_LOGIN ──────────────────────────
        List<User> neverLoggedIn = userRepository.findByVerifiedTrueAndLastLoginIsNullAndCreatedAtBefore(threeDaysAgo);
        for (User user : neverLoggedIn) {
            long daysSince = ChronoUnit.DAYS.between(user.getCreatedAt(), now);
            if (user.getUserType() == UserType.hr) {
                String severity = daysSince >= 7 ? "HIGH" : "MEDIUM";
                signals.add(new AdminDTO.FrictionSignal(
                        user.getId(), user.getEmail(), user.getUserType().name(),
                        "EMPLOYER_INACTIVE", severity,
                        "HR account verified " + daysSince + " days ago but has never signed in",
                        daysSince, 0L, "SEND_WELCOME"));
            } else {
                signals.add(new AdminDTO.FrictionSignal(
                        user.getId(), user.getEmail(), user.getUserType().name(),
                        "VERIFIED_NEVER_LOGIN", "LOW",
                        "Verified " + daysSince + " days ago but has never signed in",
                        daysSince, 0L, "SEND_WELCOME"));
            }
        }

        // ── STUCK_PENDING_LONG ────────────────────────────────────────────────
        List<User> stuckPending = userRepository.findByVerifiedFalseAndCreatedAtBefore(twoDaysAgo);
        for (User user : stuckPending) {
            long daysSince = ChronoUnit.DAYS.between(user.getCreatedAt(), now);
            String severity = daysSince >= 7 ? "HIGH" : daysSince >= 3 ? "MEDIUM" : "LOW";
            signals.add(new AdminDTO.FrictionSignal(
                    user.getId(), user.getEmail(), user.getUserType().name(),
                    "STUCK_PENDING_LONG", severity,
                    "Account unverified for " + daysSince + " days",
                    daysSince, 0L, "RESEND_VERIFICATION"));
        }

        // Sort HIGH → MEDIUM → LOW, then longest-waiting first within tier
        signals.sort((a, b) -> {
            int diff = severityOrder(a.severity()) - severityOrder(b.severity());
            if (diff != 0) return diff;
            return Long.compare(b.daysSinceCreated(), a.daysSinceCreated());
        });

        return signals;
    }

    /**
     * Send a proactive welcome / onboarding email to a user.
     * Triggered by admin from the friction-signals table.
     */
    @Transactional
    public void sendWelcome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String name = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getEmail();
        emailService.sendWelcomeEmail(user.getEmail(), user.getUserType(), name);
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case "HIGH"   -> 0;
            case "MEDIUM" -> 1;
            case "LOW"    -> 2;
            default       -> 3;
        };
    }
}
