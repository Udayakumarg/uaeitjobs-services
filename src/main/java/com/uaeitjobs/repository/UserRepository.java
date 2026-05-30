package com.uaeitjobs.repository;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByUserType(UserType userType);
    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    // ── Activity stats ────────────────────────────────────────────────────────
    long countByVerifiedTrue();
    long countByVerifiedFalse();
    long countByLastLoginAfter(OffsetDateTime cutoff);
    long countByLastLoginIsNull();
    long countByCreatedAtAfter(OffsetDateTime cutoff);

    /** Unverified users registered more than 24 h ago — "stuck pending". */
    List<User> findTop20ByVerifiedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(OffsetDateTime cutoff);

    /** Most recently registered users regardless of status. */
    List<User> findTop20ByOrderByCreatedAtDesc();

    /** Verified users who have never signed in after email confirmation. */
    List<User> findTop20ByVerifiedTrueAndLastLoginIsNullOrderByCreatedAtDesc();

    @Query("SELECT u.country AS country, COUNT(u) AS total FROM User u " +
           "WHERE u.country IS NOT NULL AND u.country <> '' " +
           "GROUP BY u.country ORDER BY COUNT(u) DESC")
    List<CountryProjection> topCountries(Pageable pageable);

    interface CountryProjection {
        String getCountry();
        Long getTotal();
    }
}
