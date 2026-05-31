package com.uaeitjobs.repository;

import com.uaeitjobs.entity.ApplicationEntity;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {
    boolean existsByJobAndUser(Job job, User user);
    Page<ApplicationEntity> findByUser(User user, Pageable pageable);
    Page<ApplicationEntity> findByJob(Job job, Pageable pageable);
    long countByJob(Job job);

    /** Returns the set of job IDs the user has applied to — used by the
     *  Browse page to render applied/unapplied sections without loading
     *  full application objects. */
    @Query("SELECT a.job.id FROM ApplicationEntity a WHERE a.user = :user")
    Set<Long> findJobIdsByUser(@Param("user") User user);

    /**
     * Compound query that verifies both existence AND ownership in one DB round-trip,
     * preventing IDOR attacks — a single generic error message is returned for both
     * "not found" and "not owned" cases to avoid information leakage.
     */
    @Query("SELECT a FROM ApplicationEntity a JOIN a.job j WHERE a.id = :appId AND j.postedBy = :owner")
    Optional<ApplicationEntity> findByIdAndJobOwner(@Param("appId") Long appId, @Param("owner") User owner);
}
