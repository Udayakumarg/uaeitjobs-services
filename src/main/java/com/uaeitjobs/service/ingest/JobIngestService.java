package com.uaeitjobs.service.ingest;

import com.uaeitjobs.entity.IngestRunLog;
import com.uaeitjobs.entity.KeywordSearchStrategy;
import com.uaeitjobs.repository.IngestRunLogRepository;
import com.uaeitjobs.repository.KeywordSearchStrategyRepository;
import com.uaeitjobs.service.ingest.pipeline.DedupResolver;
import com.uaeitjobs.service.ingest.pipeline.JobIngestPipeline;
import com.uaeitjobs.service.ingest.pipeline.JobIngestPipeline.Outcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates every ingestion pass:
 *  - Iterates each enabled JobIngestSource (Adzuna, RemoteOK, Himalayas)
 *  - Drives the JSearch keyword rotation
 *  - Pipes every IngestedJob through JobIngestPipeline (the intelligence layer)
 *  - Records per-run metrics in ingest_run_log + keyword_search_strategy counters
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestService {

    private final List<JobIngestSource> sources;
    private final JSearchSource jsearch;
    private final JobIngestPipeline pipeline;
    private final KeywordSearchStrategyRepository keywordRepo;
    private final IngestRunLogRepository runLogRepo;

    // Free tier rate limits: ~1 req/sec. These knobs let us stay safe on
    // 200 calls/month even with a 6-hour cron, and easily widen on Pro.
    @Value("${app.ingest.jsearch.delay-ms:1700}")   private long jsearchDelayMs;
    @Value("${app.ingest.jsearch.tier1-keywords:2}") private int jsearchTier1;
    @Value("${app.ingest.jsearch.tier2-keywords:1}") private int jsearchTier2;
    @Value("${app.ingest.jsearch.tier3-keywords:0}") private int jsearchTier3;
    @Value("${app.ingest.jsearch.tier4-keywords:0}") private int jsearchTier4;

    /** Prevents two concurrent ingest runs trampling each other. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Trigger {@link #runAll()} in a background thread. Returns immediately;
     * progress is observable via the {@code ingest_run_log} table.
     *
     * Concurrent invocations are no-ops while a previous run is still active
     * — the caller can poll the log to see when it finishes.
     */
    @Async
    public void runAllAsync() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Ingest already running — async trigger ignored.");
            return;
        }
        try {
            runAll();
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Accept a pre-built batch from an external scraper (Bayt, NaukriGulf, etc.)
     * and run it through the same pipeline as any other source.
     * The caller is responsible for setting stable {@code externalJobId} values
     * so L1 dedup works correctly across runs.
     */
    public Counters runExternalBatch(List<IngestedJob> jobs, String source) {
        IngestRunLog runLog = openLog(source, null);
        Counters c = new Counters();
        c.fetched = jobs.size();
        try {
            for (IngestedJob job : jobs) {
                Outcome outcome = pipeline.process(job);
                tally(c, outcome);
            }
        } catch (Exception e) {
            log.warn("External batch '{}' failed: {}", source, e.getMessage());
            runLog.setError(e.getMessage());
        } finally {
            closeLog(runLog, c);
        }
        return c;
    }

    /** Per-cron run: keyword-driven JSearch first, then the legacy sources. */
    public Map<String, Object> runAll() {
        // Close any orphaned "running" rows left by a previous restart/crash.
        OffsetDateTime staleThreshold = OffsetDateTime.now().minusMinutes(10);
        runLogRepo.findAllByFinishedAtIsNull().stream()
                .filter(r -> r.getStartedAt().isBefore(staleThreshold))
                .forEach(r -> {
                    r.setFinishedAt(OffsetDateTime.now());
                    r.setError("stale — process was interrupted");
                    runLogRepo.save(r);
                });

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();

        // ── JSearch (keyword rotation, rate-limited) ──────────
        if (jsearch.isEnabled()) {
            List<KeywordSearchStrategy> picks = pickKeywordRotation();
            int totalCreated = 0;
            boolean first = true;
            for (KeywordSearchStrategy kw : picks) {
                if (!first && jsearchDelayMs > 0) {
                    try { Thread.sleep(jsearchDelayMs); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                first = false;
                Counters c = runJSearchForKeyword(kw);
                totalCreated += c.inserted;
            }
            report.put("jsearch", totalCreated);
        } else {
            report.put("jsearch", 0);
        }

        // ── Other sources (no keywords, single fetch each) ────
        for (JobIngestSource source : sources) {
            if (!source.isEnabled()) {
                report.put(source.name(), 0);
                continue;
            }
            try {
                log.info("Ingest: starting source '{}'", source.name());
                Counters c = runGeneric(source);
                report.put(source.name(), c.inserted);
                log.info("Ingest: source '{}' done — {} fetched, {} inserted",
                        source.name(), c.fetched, c.inserted);
            } catch (Exception e) {
                log.error("Ingest: source '{}' CRASHED — {}", source.name(), e.getMessage(), e);
                report.put(source.name(), 0);
            }
        }
        return report;
    }

    // ─── JSearch keyword-driven run ──────────────────────────
    private List<KeywordSearchStrategy> pickKeywordRotation() {
        var combined = new java.util.ArrayList<KeywordSearchStrategy>();
        if (jsearchTier1 > 0) combined.addAll(keywordRepo.pickByTier(1, jsearchTier1));
        if (jsearchTier2 > 0) combined.addAll(keywordRepo.pickByTier(2, jsearchTier2));
        if (jsearchTier3 > 0) combined.addAll(keywordRepo.pickByTier(3, jsearchTier3));
        if (jsearchTier4 > 0) combined.addAll(keywordRepo.pickByTier(4, jsearchTier4));
        return combined;
    }

    @Transactional
    public Counters runJSearchForKeyword(KeywordSearchStrategy kw) {
        IngestRunLog runLog = openLog("jsearch", kw.getKeyword());
        Counters c = new Counters();
        try {
            List<IngestedJob> batch = jsearch.search(kw.getKeyword());
            c.fetched = batch.size();
            for (IngestedJob raw : batch) {
                Outcome outcome = pipeline.process(raw);
                tally(c, outcome);
            }
            // Update keyword counters
            kw.setTotalRuns(kw.getTotalRuns() + 1);
            kw.setTotalReturned(kw.getTotalReturned() + c.fetched);
            kw.setTotalInserted(kw.getTotalInserted() + c.inserted);
            kw.setTotalDuplicates(kw.getTotalDuplicates()
                    + c.duplicatesL1 + c.duplicatesL2 + c.duplicatesL3);
            kw.setLastRunAt(OffsetDateTime.now());
            kw.setLastError(null);
            keywordRepo.save(kw);
        } catch (Exception e) {
            log.warn("JSearch keyword '{}' failed: {}", kw.getKeyword(), e.getMessage());
            runLog.setError(e.getMessage());
            kw.setLastError(e.getMessage());
            keywordRepo.save(kw);
        } finally {
            closeLog(runLog, c);
        }
        return c;
    }

    // ─── Generic source run (Adzuna/RemoteOK/Himalayas) ──────
    @Transactional
    public Counters runGeneric(JobIngestSource source) {
        IngestRunLog runLog = openLog(source.name(), null);
        Counters c = new Counters();
        try {
            List<IngestedJob> batch = source.fetch();
            c.fetched = batch.size();
            for (IngestedJob raw : batch) {
                Outcome outcome = pipeline.process(raw);
                tally(c, outcome);
            }
        } catch (Exception e) {
            log.warn("Source '{}' failed: {}", source.name(), e.getMessage());
            runLog.setError(e.getMessage());
        } finally {
            closeLog(runLog, c);
        }
        return c;
    }

    // ─── Log helpers ─────────────────────────────────────────
    private IngestRunLog openLog(String source, String keyword) {
        IngestRunLog log = new IngestRunLog();
        log.setSource(source);
        log.setKeyword(keyword);
        return runLogRepo.save(log);
    }

    private void closeLog(IngestRunLog rl, Counters c) {
        try {
            rl.setFinishedAt(OffsetDateTime.now());
            rl.setFetched(c.fetched);
            rl.setRejectedHard(c.rejectedHard);
            rl.setRejectedScore(c.rejectedScore);
            rl.setDuplicatesL1(c.duplicatesL1);
            rl.setDuplicatesL2(c.duplicatesL2);
            rl.setDuplicatesL3(c.duplicatesL3);
            rl.setInserted(c.inserted);
            runLogRepo.save(rl);
        } catch (Exception e) {
            log.error("Failed to close ingest log id={}: {}", rl.getId(), e.getMessage());
        }
    }

    private static void tally(Counters c, Outcome outcome) {
        if (outcome instanceof Outcome.Inserted) {
            c.inserted++;
        } else if (outcome instanceof Outcome.Updated u) {
            switch (u.level()) {
                case L1_EXTERNAL_ID -> c.duplicatesL1++;
                case L2_HASH        -> c.duplicatesL2++;
                case L3_FUZZY       -> c.duplicatesL3++;
                default             -> {}
            }
        } else if (outcome instanceof Outcome.Rejected r) {
            switch (r.stage()) {
                case HARD  -> c.rejectedHard++;
                case SCORE -> c.rejectedScore++;
                default    -> c.rejectedHard++;
            }
        }
    }

    public static class Counters {
        public int fetched, rejectedHard, rejectedScore;
        public int duplicatesL1, duplicatesL2, duplicatesL3, inserted;
    }
}
