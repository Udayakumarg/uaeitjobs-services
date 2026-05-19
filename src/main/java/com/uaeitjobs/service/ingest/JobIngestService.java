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

    /** Title substrings that almost always indicate a non-IT role. Even
     *  Adzuna's it-jobs category occasionally lets these slip through. */
    private static final List<String> NON_IT_TITLE_BLOCKLIST = List.of(
            "insurance",
            "underwriter",
            "actuary",
            "claims",
            "license agent",
            "sales executive",
            "sales associate",
            "sales manager",
            "sales representative",
            "account executive",
            "marketing manager",
            "marketing executive",
            "social media",
            "telesales",
            "telemarketing",
            "receptionist",
            "secretary",
            "executive assistant",
            "admin assistant",
            "office assistant",
            "office manager",
            "personal assistant",
            "accountant",
            "bookkeeper",
            "tax associate",
            "financial advisor",
            "wealth manager",
            "banker",
            "teller",
            "cashier",
            "barista",
            "waiter",
            "waitress",
            "chef",
            "cook",
            "driver",
            "delivery rider",
            "warehouse",
            "logistics coordinator",
            "facilities",
            "housekeep",
            "nurse",
            "physician",
            "pharmacist",
            "dentist",
            "doctor",
            "teacher",
            "tutor",
            "lecturer",
            "translator",
            "interpreter",
            "lawyer",
            "paralegal",
            "legal counsel",
            "merchandiser",
            "store manager",
            "retail manager",
            "human resources",
            "hr manager",
            "hr executive",
            "talent acquisition",
            "recruiter",
            "real estate"
    );

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
            int rejectedTitle = 0;
            int rejectedCategory = 0;
            for (IngestedJob incoming : batch) {
                if (incoming.applyUrl() == null || incoming.applyUrl().isBlank()) continue;
                if (jobRepository.existsByApplyUrl(incoming.applyUrl())) continue;
                if (!isItRole(incoming.title())) { rejectedTitle++; continue; }
                String category = JobCategoryClassifier.classify(incoming.title(), incoming.description());
                if (category == null || JobCategoryClassifier.OTHER.equals(category)) {
                    rejectedCategory++; continue;
                }
                try {
                    persist(incoming, category);
                    created++;
                } catch (Exception ex) {
                    log.warn("Failed to persist '{}' from {}: {}", incoming.title(), source.name(), ex.getMessage());
                }
            }
            if (rejectedTitle + rejectedCategory > 0) {
                log.info("Ingest [{}] — rejected {} non-IT title(s) and {} uncategorisable role(s).",
                        source.name(), rejectedTitle, rejectedCategory);
            }
            report.put(source.name(), created);
            log.info("Ingest [{}] — fetched={} created={}", source.name(), batch.size(), created);
        }
        return report;
    }

    private void persist(IngestedJob incoming, String category) {
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
        job.setJobCategory(category);
        jobRepository.save(job);
    }

    /** Rejects job titles that are almost certainly not IT roles. */
    private static boolean isItRole(String title) {
        if (title == null || title.isBlank()) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        for (String blocked : NON_IT_TITLE_BLOCKLIST) {
            if (lower.contains(blocked)) return false;
        }
        return true;
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
