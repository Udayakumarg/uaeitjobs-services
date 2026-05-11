package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "hr_subscriptions")
public class HRSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private String tier;
    @Column(name = "featured_jobs_limit")
    private int featuredJobsLimit;
    @Column(name = "featured_jobs_used")
    private int featuredJobsUsed;
    @Column(name = "monthly_job_limit")
    private int monthlyJobLimit;
    @Column(name = "jobs_posted")
    private int jobsPosted;
    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "is_active")
    private boolean active = true;
}
