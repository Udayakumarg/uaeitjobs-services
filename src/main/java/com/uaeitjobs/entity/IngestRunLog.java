package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "ingest_run_log")
public class IngestRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(length = 255)
    private String keyword;

    @Column(nullable = false) private int fetched;
    @Column(name = "rejected_hard",  nullable = false) private int rejectedHard;
    @Column(name = "rejected_score", nullable = false) private int rejectedScore;
    @Column(name = "duplicates_l1",  nullable = false) private int duplicatesL1;
    @Column(name = "duplicates_l2",  nullable = false) private int duplicatesL2;
    @Column(name = "duplicates_l3",  nullable = false) private int duplicatesL3;
    @Column(nullable = false) private int inserted;

    @Column(columnDefinition = "text")
    private String error;
}
