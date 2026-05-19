package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;
    @Column(nullable = false)
    private String title;
    @Column(name = "company_name", nullable = false)
    private String companyName;
    @Column(nullable = false)
    private String description;
    private String requirements;
    @Column(name = "salary_min")
    private Integer salaryMin;
    @Column(name = "salary_max")
    private Integer salaryMax;
    @Column(name = "salary_currency")
    private String salaryCurrency = "AED";
    @Column(name = "job_type")
    private String jobType;
    @Column(name = "experience_level")
    private String experienceLevel;
    @Column(name = "location_uae")
    private String locationUae;
    /** UAE filter dimensions — see V5 migration for accepted values. */
    @Column(name = "visa_type")
    private String visaType;
    @Column(name = "emirate")
    private String emirate;
    @Column(name = "immediate_joiner", nullable = false)
    private boolean immediateJoiner;
    @Column(name = "remote_uae", nullable = false)
    private boolean remoteUae;
    /** Coarse role category — backend, frontend, qa, devops, etc. See JobCategory util for accepted values. */
    @Column(name = "job_category")
    private String jobCategory;
    /** External URL the user is sent to when clicking Apply (LinkedIn / company site). */
    @Column(name = "apply_url", columnDefinition = "text")
    private String applyUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String skills = "[]";
    @Column(name = "linkedin_url")
    private String linkedinUrl;
    private String source = "manual";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "is_featured", nullable = false)
    private boolean featured;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
}
