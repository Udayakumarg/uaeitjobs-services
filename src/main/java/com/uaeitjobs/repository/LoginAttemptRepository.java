package com.uaeitjobs.repository;

import com.uaeitjobs.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /** Total attempts since a given point in time (for today's health stats). */
    long countByCreatedAtAfter(OffsetDateTime after);

    /** Successful or failed attempts since a given point in time. */
    long countBySuccessAndCreatedAtAfter(boolean success, OffsetDateTime after);

    /**
     * Failure counts grouped by reason — used for the breakdown bar chart.
     * Returns [failureReason (String), count (Long)] pairs.
     */
    @Query("SELECT la.failureReason, COUNT(la) FROM LoginAttempt la " +
           "WHERE la.success = false AND la.createdAt > :after " +
           "GROUP BY la.failureReason ORDER BY COUNT(la) DESC")
    List<Object[]> failureBreakdownSince(@Param("after") OffsetDateTime after);

    /**
     * Returns [userId (Long), failCount (Long)] for registered users who have
     * accumulated at least minCount failures since the given time.
     * Used for the REPEATED_FAILURES friction signal.
     */
    @Query("SELECT la.user.id, COUNT(la) FROM LoginAttempt la " +
           "WHERE la.success = false AND la.createdAt > :since AND la.user IS NOT NULL " +
           "GROUP BY la.user.id HAVING COUNT(la) >= :minCount ORDER BY COUNT(la) DESC")
    List<Object[]> usersWithRepeatedFailuresSince(
            @Param("since") OffsetDateTime since,
            @Param("minCount") long minCount);
}
