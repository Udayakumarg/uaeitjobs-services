package com.uaeitjobs.repository;

import com.uaeitjobs.entity.RefreshToken;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    /** Count of non-revoked, non-expired tokens — proxy for active sessions. */
    long countByRevokedFalseAndExpiresAtAfter(OffsetDateTime now);

    @Modifying
    void deleteByUser(User user);

    /** Revoke all active refresh tokens for a user — called after password change. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user = :user AND t.revoked = false")
    void revokeAllByUser(@Param("user") User user);

    /**
     * Bulk-delete tokens that have already expired or been explicitly revoked.
     * Called nightly by {@code TokenCleanupScheduler} to keep the table lean.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now OR t.revoked = true")
    int deleteExpiredOrRevoked(@Param("now") OffsetDateTime now);
}
