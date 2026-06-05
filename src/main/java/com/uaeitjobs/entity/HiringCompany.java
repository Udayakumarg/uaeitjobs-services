package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Directory entry for a UAE organisation that hires for IT / technology roles.
 *
 * <p>Powers the public {@code /companies} listing, the authenticated submission
 * form, and the admin moderation queue. Only rows with {@code status = APPROVED}
 * appear on the public site.
 */
@Getter
@Setter
@Entity
@Table(name = "hiring_companies")
public class HiringCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String category;
    private String city;

    @Column(name = "careers_url", nullable = false, columnDefinition = "text")
    private String careersUrl;

    @Column(name = "website_url", columnDefinition = "text")
    private String websiteUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "tech_focus", columnDefinition = "text")
    private String techFocus;

    /** ACTIVE_HIRING | FREQUENT_HIRING | OCCASIONAL — see V17 CHECK constraint. */
    @Column(name = "hiring_status", nullable = false)
    private String hiringStatus = "OCCASIONAL";

    @Column(nullable = false)
    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HiringCompanyStatus status = HiringCompanyStatus.PENDING;

    /** True once a human (admin) or trusted source has confirmed the careers URL. */
    @Column(name = "url_verified", nullable = false)
    private boolean urlVerified;

    /** Nullable: seed rows have no submitter. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id")
    private User submittedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
