package com.uaeitjobs.service.ingest;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.util.JobCategoryClassifier;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Iterates every registered JobIngestSource, deduplicates by apply_url,
 * runs auto-categorisation, and persists new postings. Returns a summary
 * map per source so we can log + expose via the admin endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestService {

    private final List<JobIngestSource> sources;
    private final JobRepository jobRepository;

    @Transactional
    public Map<String, Integer> runAll() {
        return runAll(sources);
    }

    @Transactional
    public Map<String, Integer> runAll(List<JobIngestSource> sourcesToRun) {
        java.util.LinkedHashMap<String, Integer> report = new java.util.LinkedHashMap<>();
        for (JobIngestSource source : sourcesToRun) {
            int created = 0;
            if (!source.isEnabled()) {
                log.debug("Source {} is disabled — skipping.", source.name());
                report.put(source.name(), 0);
                continue;
            }
            List<IngestedJob> batch;
            try {
                batch = source.fetch();
            } catch (Exception ex) {
                log.warn("Source {} threw on fetch: {}", source.name(), ex.getMessage());
                report.put(source.name(), 0);
                continue;
            }
            for (IngestedJob incoming : batch) {
                if (incoming.applyUrl() == null || incoming.applyUrl().isBlank()) continue;
                if (jobRepository.existsByApplyUrl(incoming.applyUrl())) continue;
                try {
                    persist(incoming);
                    created++;
                } catch (Exception ex) {
                    log.warn("Failed to persist '{}' from {}: {}", incoming.title(), source.name(), ex.getMessage());
                }
            }
            report.put(source.name(), created);
            log.info("Ingest [{}] — fetched={} created={}", source.name(), batch.size(), created);
        }
        return report;
    }

    private void persist(IngestedJob incoming) {
        Job job = new Job();
        job.setSlug(uniqueSlug(incoming.title()));
        job.setTitle(incoming.title());
        job.setCompanyName(incoming.companyName());
        job.setDescription(incoming.description());
        job.setRequirements(incoming.requirements());
        job.setSalaryMin(incoming.salaryMin());
        job.setSalaryMax(incoming.salaryMax());
        job.setSalaryCurrency(incoming.salaryCurrency() == null ? "AED" : incoming.salaryCurrency());
        job.setJobType(incoming.jobType());
        job.setExperienceLevel(incoming.experienceLevel());
        job.setLocationUae(incoming.locationUae());
        job.setSkills("[]");
        job.setSource(incoming.source());
        job.setApplyUrl(incoming.applyUrl());
        job.setLinkedinUrl(null);
        job.setActive(true);
        job.setFeatured(false);
        job.setExpiresAt(OffsetDateTime.now().plusDays(30));
        job.setEmirate(incoming.emirate());
        job.setRemoteUae(incoming.remoteUae());
        job.setImmediateJoiner(false);
        String inferred = JobCategoryClassifier.classify(incoming.title(), incoming.description());
        job.setJobCategory(inferred != null ? inferred : JobCategoryClassifier.OTHER);
        jobRepository.save(job);
    }

    private String uniqueSlug(String title) {
        String base = SlugGenerator.from(title).toLowerCase(Locale.ROOT);
        String slug = base;
        int counter = 1;
        while (jobRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
            if (counter > 50) {
                slug = base + "-" + System.currentTimeMillis();
                break;
            }
        }
        return slug;
    }
}
