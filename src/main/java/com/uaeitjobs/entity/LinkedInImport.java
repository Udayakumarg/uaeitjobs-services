package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "linkedin_imports")
public class LinkedInImport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "linkedin_job_url", nullable = false)
    private String linkedinJobUrl;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_job_id")
    private Job importedJob;
    private String status = "pending";
    @Column(name = "error_message")
    private String errorMessage;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
