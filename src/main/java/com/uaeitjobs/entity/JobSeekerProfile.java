package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "job_seeker_profiles")
public class JobSeekerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "cv_url")
    private String cvUrl;
    private String headline;
    private String summary;
    @Column(name = "years_experience")
    private Integer yearsExperience;
    @Column(name = "visa_status")
    private String visaStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String skills = "[]";

    /** Free-form experience text entered by the user (plain text, not JSON). */
    @Column(columnDefinition = "text", nullable = false)
    private String experience = "";

    /** Free-form education text entered by the user (plain text, not JSON). */
    @Column(columnDefinition = "text", nullable = false)
    private String education = "";
}
