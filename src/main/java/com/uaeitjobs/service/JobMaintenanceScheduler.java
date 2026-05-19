package com.uaeitjobs.service;

import com.uaeitjobs.service.ingest.JobIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Scheduled tasks that keep the job catalog healthy without manual ops.
 *
 * All schedules use UTC. Override via application.yml properties of the
 * form `app.cron.*` if you ever need a different cadence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMaintenanceScheduler {

    private final JdbcTemplate jdbc;
    private final JobIngestService jobIngestService;

    /**
     * Deactivate jobs that have crossed their expires_at timestamp.
     * Runs daily at 02:00 UTC (06:00 GST).
     */
    @Scheduled(cron = "${app.cron.expire-stale-jobs:0 0 2 * * *}", zone = "UTC")
    @Transactional
    public void expireStaleJobs() {
        int updated = jdbc.update(
                "update jobs set is_active = false, updated_at = now() " +
                "where is_active = true and expires_at is not null and expires_at < now()"
        );
        if (updated > 0) {
            log.info("Cron: expired {} stale job listing(s).", updated);
        }
    }

    /**
     * Touch updated_at on active jobs older than 7 days so the sitemap's
     * <lastmod> value stays fresh — encourages Google to re-crawl them.
     * Runs daily at 02:15 UTC.
     */
    @Scheduled(cron = "${app.cron.refresh-active-jobs:0 15 2 * * *}", zone = "UTC")
    @Transactional
    public void refreshLongRunningJobs() {
        int updated = jdbc.update(
                "update jobs set updated_at = now() " +
                "where is_active = true and updated_at < now() - interval '7 days'"
        );
        if (updated > 0) {
            log.info("Cron: refreshed lastmod on {} long-running job(s).", updated);
        }
    }

    /**
     * Real job ingestion — fans out to every enabled JobIngestSource
     * (Adzuna, RemoteOK, …). Runs every 6 hours by default so we stay
     * well inside Adzuna's free-tier quota of 1000 calls/day.
     */
    @Scheduled(cron = "${app.cron.ingest-jobs:0 0 */6 * * *}", zone = "UTC")
    public void ingestExternalJobs() {
        Map<String, Integer> report = jobIngestService.runAll();
        int total = report.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 0) {
            log.info("Cron: ingested {} new job(s) across sources: {}", total, report);
        } else {
            log.info("Cron: ingest finished with no new jobs ({})", report);
        }
    }
}
