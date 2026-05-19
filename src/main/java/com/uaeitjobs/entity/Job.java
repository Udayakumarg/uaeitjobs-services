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

    // ── Intelligence layer (V7) ─────────────────────────────────
    /** Stable identifier from the source API — primary dedup key. */
    @Column(name = "external_job_id")
    private String externalJobId;
    /** Same value as `source` but kept explicit for the unique-index pair. */
    @Column(name = "external_source")
    private String externalSource;
    /** SHA-256 of normalized(company + title + city) — L2 dedup key. */
    @Column(name = "dedup_hash")
    private String dedupHash;
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;
    @Column(name = "duplicate_source_count", nullable = false)
    private int duplicateSourceCount = 1;
    /** Original publisher reported by JSearch (LinkedIn, Bayt, …). */
    @Column(name = "publisher")
    private String publisher;
    @Column(name = "raw_title")
    private String rawTitle;
    @Column(name = "normalized_title")
    private String normalizedTitle;
    @Column(name = "normalized_company_name")
    private String normalizedCompanyName;
    @Column(name = "city")
    private String city;
    @Column(name = "country")
    private String country = "AE";
    /** remote | hybrid | onsite */
    @Column(name = "work_mode")
    private String workMode;
    /** intern | junior | mid | senior | lead | manager | architect */
    @Column(name = "seniority")
    private String seniority;
    @Column(name = "relevance_score", nullable = false)
    private int relevanceScore = 100;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "relevance_reasons", columnDefinition = "jsonb")
    private String relevanceReasons;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "technologies", columnDefinition = "jsonb", nullable = false)
    private String technologies = "[]";
    @Column(name = "visa_sponsorship")
    private Boolean visaSponsorship;
    @Column(name = "easy_apply")
    private Boolean easyApply;
    @Column(name = "relocation_support")
    private Boolean relocationSupport;

    // ── Fast tech filters ─────────────────────────────────────
    @Column(name = "has_java",        nullable = false) private boolean hasJava;
    @Column(name = "has_python",      nullable = false) private boolean hasPython;
    @Column(name = "has_javascript",  nullable = false) private boolean hasJavascript;
    @Column(name = "has_typescript",  nullable = false) private boolean hasTypescript;
    @Column(name = "has_csharp",      nullable = false) private boolean hasCsharp;
    @Column(name = "has_react",       nullable = false) private boolean hasReact;
    @Column(name = "has_angular",     nullable = false) private boolean hasAngular;
    @Column(name = "has_node",        nullable = false) private boolean hasNode;
    @Column(name = "has_spring_boot", nullable = false) private boolean hasSpringBoot;
    @Column(name = "has_selenium",    nullable = false) private boolean hasSelenium;
    @Column(name = "has_playwright",  nullable = false) private boolean hasPlaywright;
    @Column(name = "has_aws",         nullable = false) private boolean hasAws;
    @Column(name = "has_azure",       nullable = false) private boolean hasAzure;
    @Column(name = "has_kubernetes",  nullable = false) private boolean hasKubernetes;
    @Column(name = "has_docker",      nullable = false) private boolean hasDocker;
    @Column(name = "has_postgresql",  nullable = false) private boolean hasPostgresql;
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
