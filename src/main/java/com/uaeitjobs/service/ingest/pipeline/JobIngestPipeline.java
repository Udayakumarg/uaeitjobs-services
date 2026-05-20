package com.uaeitjobs.service.ingest.pipeline;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.service.ingest.IngestedJob;
import com.uaeitjobs.service.ingest.pipeline.description.DescriptionFormatterRegistry;
import com.uaeitjobs.util.JobCategoryClassifier;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The intelligence layer's main entry point.
 *
 *   Source (Adzuna/RemoteOK/JSearch) → IngestedJob → JobIngestPipeline.process()
 *
 * The pipeline:
 *   1. Hard reject obvious noise (non-UAE, blocklisted titles)
 *   2. Normalise title / company / city / seniority / work-mode
 *   3. Extract technologies → JSONB + has_* booleans
 *   4. Score 0-100 — drop below 70
 *   5. Compute dedup hash and run L1 / L2 / L3 resolution
 *   6. INSERT new or UPDATE existing with last_seen_at + duplicate_source_count++
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestPipeline {

    private final Normalizers normalizers;
    private final TechnologyExtractor techExtractor;
    private final RelevanceScorer scorer;
    private final DedupResolver dedup;
    private final JobDescriptionFormatter descriptionFormatter;
    private final DescriptionFormatterRegistry formatterRegistry;
    private final JobRepository jobRepository;

    public sealed interface Outcome {
        record Inserted(Job job)                          implements Outcome {}
        record Updated(Job job, DedupResolver.Level level) implements Outcome {}
        record Rejected(Stage stage, String reason)       implements Outcome {}
    }
    public enum Stage { HARD, SCORE, PERSIST }

    @Transactional
    public Outcome process(IngestedJob incoming) {
        // ── 1. Hard reject ────────────────────────────────────────
        if (scorer.hardReject(incoming.title(), incoming.locationUae(), null, incoming.description())) {
            return new Outcome.Rejected(Stage.HARD, "non-UAE or blocklisted title");
        }

        // ── 2. Normalise ──────────────────────────────────────────
        String rawTitle  = incoming.title();
        String normTitle = normalizers.normalizeTitle(rawTitle);
        String normCompany = normalizers.normalizeCompany(incoming.companyName());
        Normalizers.LocaleInfo locale = normalizers.normalizeLocation(
                incoming.locationUae(), incoming.emirate(), null);
        String seniority = normalizers.classifySeniority(rawTitle, incoming.description());
        String workMode  = normalizers.classifyWorkMode(rawTitle, incoming.description(), incoming.remoteUae());

        // ── 3. Technology extraction (operates on a new Job we'll mutate) ──
        Job job = new Job();
        job.setRawTitle(rawTitle);
        job.setNormalizedTitle(normTitle);
        job.setNormalizedCompanyName(normCompany);
        job.setCity(locale.city());
        job.setEmirate(locale.emirate());
        job.setCountry(locale.country());
        job.setSeniority(seniority);
        job.setWorkMode(workMode);

        String haystack = (safe(rawTitle) + " " + safe(incoming.description()) + " " + safe(incoming.requirements()))
                .toLowerCase(Locale.ROOT);
        techExtractor.applyTo(job, haystack);

        int techCount = techExtractor.extractKeys(haystack).size();

        // ── 4. Score ─────────────────────────────────────────────
        List<RelevanceScorer.Reason> reasons = new ArrayList<>();
        int score = scorer.score(rawTitle, incoming.description(), techCount, reasons);
        job.setRelevanceScore(score);
        job.setRelevanceReasons(serialiseReasons(reasons));
        if (score < RelevanceScorer.MIN_SCORE) {
            return new Outcome.Rejected(Stage.SCORE, "score=" + score);
        }

        // ── 5. Dedup ─────────────────────────────────────────────
        String hash = dedup.hash(normCompany, normTitle, locale.city());
        DedupResolver.Match match = dedup.resolve(
                incoming.source(), incoming.externalJobId(),
                hash, normTitle, normCompany, locale.city());

        OffsetDateTime now = OffsetDateTime.now();
        if (match.isDuplicate()) {
            Job existing = match.existing();
            existing.setLastSeenAt(now);
            existing.setDuplicateSourceCount(existing.getDuplicateSourceCount() + 1);
            // refresh the apply URL & publisher in case the new source has a more direct link
            if (incoming.applyUrl() != null && !incoming.applyUrl().isBlank()) {
                existing.setApplyUrl(incoming.applyUrl());
            }
            if (incoming.publisher() != null) existing.setPublisher(incoming.publisher());
            jobRepository.save(existing);
            return new Outcome.Updated(existing, match.level());
        }

        // ── 6. Persist new ────────────────────────────────────────
        try {
            populateNewJob(job, incoming, hash, now);
            jobRepository.save(job);
            return new Outcome.Inserted(job);
        } catch (RuntimeException e) {
            log.warn("Persist failed for '{}': {}", rawTitle, e.getMessage());
            return new Outcome.Rejected(Stage.PERSIST, e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────
    private void populateNewJob(Job job, IngestedJob incoming, String dedupHash, OffsetDateTime now) {
        // Title for SEO/UI is the normalized canonical version; raw_title is preserved.
        job.setSlug(uniqueSlug(job.getNormalizedTitle()));
        job.setTitle(job.getNormalizedTitle());
        // CompanyName retains the original brand for display; normalized_company_name is what we filter on.
        job.setCompanyName(incoming.companyName());

        // Three-part description handling:
        //  - description           → plain text  (SEO, JSON-LD, sitemap)
        //  - description_sections  → JSONB array of {heading, items[]} (legacy renderer)
        //  - description_html      → sanitised HTML from the per-vendor formatter
        //                           (primary UI rendering)
        String raw = safe(incoming.description());
        var sections = descriptionFormatter.parseSections(raw);
        job.setDescription(descriptionFormatter.format(raw));
        job.setDescriptionSections(descriptionFormatter.toJson(sections));
        job.setDescriptionHtml(
                formatterRegistry.forVendor(incoming.source()).toHtml(raw));

        job.setRequirements(incoming.requirements());
        job.setSalaryMin(incoming.salaryMin());
        job.setSalaryMax(incoming.salaryMax());
        job.setSalaryCurrency(incoming.salaryCurrency() == null ? "AED" : incoming.salaryCurrency());
        job.setJobType(incoming.jobType() == null ? "full_time" : incoming.jobType());
        job.setExperienceLevel(incoming.experienceLevel());
        // Keep location_uae for backward-compatibility with the existing /jobs?location= filter.
        job.setLocationUae(incoming.locationUae());
        job.setSkills("[]");
        job.setSource(incoming.source());
        job.setExternalSource(incoming.source());
        job.setExternalJobId(incoming.externalJobId());
        job.setDedupHash(dedupHash);
        job.setLastSeenAt(now);
        job.setDuplicateSourceCount(1);
        job.setPublisher(incoming.publisher());
        job.setApplyUrl(incoming.applyUrl());
        job.setActive(true);
        job.setFeatured(false);
        job.setExpiresAt(now.plusDays(30));
        // Override remote_uae only if the workMode classifier disagrees.
        job.setRemoteUae("remote".equals(job.getWorkMode()) || incoming.remoteUae());
        job.setImmediateJoiner(false);
        // Job category — re-use the existing classifier on the normalized title for stability.
        String category = JobCategoryClassifier.classify(job.getNormalizedTitle(), incoming.description());
        job.setJobCategory(category == null ? JobCategoryClassifier.OTHER : category);
    }

    private String uniqueSlug(String title) {
        String base = SlugGenerator.from(title).toLowerCase(Locale.ROOT);
        String slug = base;
        int n = 1;
        while (jobRepository.existsBySlug(slug)) {
            slug = base + "-" + n++;
            if (n > 100) { slug = base + "-" + System.currentTimeMillis(); break; }
        }
        return slug;
    }

    private static String serialiseReasons(List<RelevanceScorer.Reason> reasons) {
        if (reasons.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var r : reasons) {
            if (!first) sb.append(',');
            sb.append("{\"rule\":\"").append(r.rule().replace("\"", "\\\""))
                    .append("\",\"delta\":").append(r.delta()).append('}');
            first = false;
        }
        return sb.append(']').toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
