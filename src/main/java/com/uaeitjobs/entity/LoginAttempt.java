package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the email entered doesn't correspond to any registered account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email_entered", nullable = false)
    private String emailEntered;

    @Column(nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private LoginFailureReason failureReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
