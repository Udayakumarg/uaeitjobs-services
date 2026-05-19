package com.uaeitjobs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row per keyword the ingestion layer can send to JSearch.
 * Drives keyword rotation, weighting, and per-keyword performance metrics.
 */
@Getter
@Setter
@Entity
@Table(name = "keyword_search_strategy")
public class KeywordSearchStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyword;

    /** 1 (highest priority) → 4 (occasional enrichment). */
    @Column(nullable = false)
    private short tier;

    /** role | technology | experience | location */
    @Column(length = 60)
    private String category;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "total_runs", nullable = false)
    private long totalRuns;

    @Column(name = "total_returned", nullable = false)
    private long totalReturned;

    @Column(name = "total_inserted", nullable = false)
    private long totalInserted;

    @Column(name = "total_duplicates", nullable = false)
    private long totalDuplicates;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
