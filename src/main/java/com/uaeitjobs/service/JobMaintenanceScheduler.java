package com.uaeitjobs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Placeholder for future scrape automation — wire in your LinkedIn /
     * RSS / aggregator ingestion here once a source list is available.
     * Schedule disabled by default so it does nothing until configured.
     */
    @Scheduled(cron = "${app.cron.import-external-jobs:-}", zone = "UTC")
    public void importExternalJobs() {
        log.debug("Cron: external-import slot fired — no source configured.");
    }
}
