package com.uaeitjobs.service;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.service.ingest.pipeline.description.DescriptionFormatterRegistry;
import com.uaeitjobs.service.ingest.pipeline.description.HeuristicDescriptionFormatter;
import com.uaeitjobs.service.ingest.pipeline.description.JobDescriptionFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background upgrader for the {@code jobs.description_html} column.
 *
 * <p>The synchronous path in {@link JobService#create} stores a quick
 * heuristic-formatted HTML so the API returns within milliseconds —
 * useful for HR UX (e.g. LinkedIn imports that block the spinner). This
 * service then runs the slower LLM-backed formatter off the request
 * thread and overwrites {@code description_html} with the better
 * structured output when it returns.
 *
 * <p>Failures are swallowed: the heuristic HTML stays in place and the
 * job remains valid. Logged at WARN with the job id so an operator can
 * spot a stuck LLM provider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDescriptionEnhancer {

    private final DescriptionFormatterRegistry formatterRegistry;
    private final HeuristicDescriptionFormatter heuristic;
    private final JobRepository jobRepository;

    /**
     * Re-format the supplied raw text via the registered formatter for
     * {@code source} (typically the LLM when enabled) and persist the
     * result on the job row identified by {@code jobId}. Runs on the
     * Spring async executor — caller returns immediately.
     */
    @Async
    @Transactional
    public void enhance(Long jobId, String rawCombined, String source) {
        JobDescriptionFormatter formatter = formatterRegistry.forVendor(source);
        // Skip the round-trip if the registry would just return the heuristic
        // again — we already stored its output synchronously.
        if (formatter == heuristic) {
            log.debug("Async enhancement skipped for job {} — registry returned heuristic", jobId);
            return;
        }
        try {
            String html = formatter.toHtml(rawCombined);
            if (html == null || html.isBlank()) {
                log.debug("Async formatter returned blank for job {} — keeping heuristic HTML", jobId);
                return;
            }
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Async enhancement: job {} disappeared before update", jobId);
                return;
            }
            job.setDescriptionHtml(html);
            jobRepository.save(job);
            log.info("Async LLM enhancement applied to job {} ({} chars)", jobId, html.length());
        } catch (Exception e) {
            log.warn("Async LLM enhancement failed for job {} — heuristic HTML retained: {}",
                    jobId, e.getMessage());
        }
    }
}
