package com.uaeitjobs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.dto.PublisherDTO;
import com.uaeitjobs.dto.StatsDTO;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.JobMapper;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import com.uaeitjobs.service.ingest.pipeline.CompanyLogoResolver;
import com.uaeitjobs.service.ingest.pipeline.description.HeuristicDescriptionFormatter;
import com.uaeitjobs.util.JobCategoryClassifier;
import com.uaeitjobs.util.SecurityUtils;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobMapper jobMapper;
    private final CompanyLogoResolver logoResolver;
    private final SubscriptionService subscriptionService;
    private final HeuristicDescriptionFormatter heuristicFormatter;
    private final AsyncDescriptionEnhancer asyncDescriptionEnhancer;
    private final ObjectMapper objectMapper;

    public Page<JobDTO.JobResponse> list(Pageable pageable) {
        boolean authed = SecurityUtils.isAuthenticated();
        return jobRepository.findByActiveTrue(pageable)
                .map(job -> authed ? jobMapper.toResponse(job) : jobMapper.toResponse(job).withMaskedApply());
    }

    /** Admin-only: list all jobs (active + archived) with optional search and active filter. */
    public Page<JobDTO.JobResponse> adminList(String q, Boolean active, Pageable pageable) {
        Page<Job> page;
        if (active != null && q != null && !q.isBlank()) {
            page = jobRepository.adminSearch(q.trim(), active, pageable);
        } else if (active != null) {
            page = jobRepository.findByActive(active, pageable);
        } else if (q != null && !q.isBlank()) {
            page = jobRepository.adminSearchAll(q.trim(), pageable);
        } else {
            page = jobRepository.findAll(pageable);
        }
        return page.map(jobMapper::toResponse);
    }

    @Transactional
    public JobDTO.JobResponse detail(Long id) {
        Job job = jobRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        jobRepository.incrementViewCount(id);
        job.setViewCount(job.getViewCount() + 1);
        JobDTO.JobResponse resp = jobMapper.toResponse(job);
        return SecurityUtils.isAuthenticated() ? resp : resp.withMaskedApply();
    }

    public Page<JobDTO.JobResponse> filter(String type, String level, String location, String skill, Pageable pageable) {
        return filter(type, level, location, skill, null, null, null, null, null, pageable);
    }

    public Page<JobDTO.JobResponse> filter(String type, String level, String location, String skill,
                                           String visaType, String emirate, Boolean immediateJoiner, Boolean remoteUae,
                                           String category, Pageable pageable) {
        // Strip the JPA-style Sort — the native filter query owns its own
        // ORDER BY, and Spring would otherwise append `order by createdAt`
        // (camelCase property) which fails against the snake_case column.
        boolean  authed   = SecurityUtils.isAuthenticated();
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return jobRepository.filter(
                blankToNull(type),
                blankToNull(level),
                blankToNull(location),
                blankToNull(skill),
                blankToNull(visaType),
                blankToNull(emirate),
                Boolean.TRUE.equals(immediateJoiner) ? Boolean.TRUE : null,
                Boolean.TRUE.equals(remoteUae) ? Boolean.TRUE : null,
                blankToNull(category),
                unsorted).map(job -> authed ? jobMapper.toResponse(job) : jobMapper.toResponse(job).withMaskedApply());
    }

    /**
     * Multi-select filter — all dimensions accept a List of values. Empty or
     * null lists mean "no filter". When {@code q} is non-blank the results are
     * also filtered and ranked by full-text relevance, so search + facets run
     * in a single DB round-trip. Results are fully filtered on the DB side so
     * the 80-result page cap never silently hides matching jobs.
     */
    public Page<JobDTO.JobResponse> filterMulti(java.util.List<String> emirate,
                                                java.util.List<String> category,
                                                java.util.List<String> levels,
                                                java.util.List<String> jobTypes,
                                                Boolean remoteUae,
                                                Boolean immediateJoiner,
                                                String postedAfter,
                                                Integer salaryMin,
                                                Integer salaryMax,
                                                String sortBy,
                                                String q,
                                                java.util.List<String> publishers,
                                                String company,
                                                String applyMode,
                                                Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        boolean  authed   = SecurityUtils.isAuthenticated();
        return jobRepository.filterMulti(
                joinOrEmpty(emirate),
                joinOrEmpty(category),
                joinOrEmpty(levels),
                joinOrEmpty(jobTypes),
                Boolean.TRUE.equals(remoteUae)       ? Boolean.TRUE : null,
                Boolean.TRUE.equals(immediateJoiner) ? Boolean.TRUE : null,
                blankToEmpty(postedAfter),
                salaryMin,
                salaryMax,
                blankToNull(sortBy) == null ? "newest" : sortBy,
                blankToEmpty(q),
                joinOrEmpty(publishers),
                blankToEmpty(company),
                normalizeApplyMode(applyMode),
                unsorted
        ).map(job -> authed ? jobMapper.toResponse(job) : jobMapper.toResponse(job).withMaskedApply());
    }

    private static String joinOrEmpty(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    /**
     * "easy" (no external apply_url/linkedin_url — applies within the platform)
     * or "external" (redirects to the source site). Anything else is treated as
     * no filter rather than interpolated into the query, since an unrecognised
     * value would otherwise match neither branch and silently zero the results.
     */
    private static String normalizeApplyMode(String value) {
        String v = blankToEmpty(value).toLowerCase(java.util.Locale.ROOT);
        return v.equals("easy") || v.equals("external") ? v : "";
    }

    public Page<JobDTO.JobResponse> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return list(pageable);
        }
        // Search native query has its own ORDER BY (ts_rank) — strip Sort.
        boolean  authed   = SecurityUtils.isAuthenticated();
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return jobRepository.search(query, unsorted)
                .map(job -> authed ? jobMapper.toResponse(job) : jobMapper.toResponse(job).withMaskedApply());
    }

    @Transactional
    /** Fast pre-scrape duplicate check used by the import-preview endpoint. */
    public boolean existsByApplyUrl(String url) {
        return url != null && !url.isBlank() && jobRepository.existsByApplyUrl(url.strip());
    }

    public JobDTO.JobResponse create(JobDTO.JobRequest request, User user) {
        return create(request, user, "manual");
    }

    @Transactional
    public JobDTO.JobResponse create(JobDTO.JobRequest request, User user, String source) {
        if (!user.isVerified()) {
            throw new ValidationException("Verify email before posting jobs");
        }
        subscriptionService.assertCanPost(user);
        // Safety-net duplicate guard (primary check is at import-preview time)
        String applyUrl = request.applyUrl() != null ? request.applyUrl().strip() : null;
        if (applyUrl != null && !applyUrl.isBlank() && jobRepository.existsByApplyUrl(applyUrl)) {
            throw new ValidationException("A job with this URL has already been posted");
        }
        Job job = apply(new Job(), request);
        job.setSlug(uniqueSlug(request.title()));
        job.setPostedBy(user);
        job.setSource(source);
        OffsetDateTime now = OffsetDateTime.now();
        job.setPostedAt(now);   // HR-posted: original date = moment of submission
        job.setLastSeenAt(now);
        // Two-stage formatting:
        //   1. Heuristic runs synchronously so the API responds in < 50ms
        //      — the HR user sees the job appear immediately.
        //   2. The async enhancer re-runs the registered formatter (LLM
        //      when enabled) and overwrites description_html. Failures
        //      are swallowed so the heuristic version always sticks.
        String combinedRaw = combineForFormatting(request.description(), request.requirements());
        job.setDescriptionHtml(heuristicFormatter.toHtml(combinedRaw));
        Job saved = jobRepository.save(job);
        asyncDescriptionEnhancer.enhance(saved.getId(), combinedRaw, source);
        subscriptionService.incrementPosted(user);
        return jobMapper.toResponse(saved);
    }

    @Transactional
    public JobDTO.JobResponse update(Long id, JobDTO.JobRequest request, User user) {
        Job job = ownedJob(id, user);
        return jobMapper.toResponse(apply(job, request));
    }

    @Transactional
    public void softDelete(Long id, User user) {
        ownedJob(id, user).setActive(false);
    }

    public Page<JobDTO.JobResponse> postedBy(User user, Pageable pageable) {
        // Soft-deleted jobs (active=false) stay in the DB for audit but are
        // hidden from the HR's "My Posted Jobs" list — clicking delete should
        // make a job disappear from the HR's view immediately.
        return jobRepository.findByPostedByAndActiveTrue(user, pageable).map(jobMapper::toResponse);
    }

    public StatsDTO stats() {
        return new StatsDTO(jobRepository.countByActiveTrue(), userRepository.count(), jobRepository.countActiveCompanies());
    }

    public List<String> skills(String query) {
        return jobRepository.findSkills(query == null ? "" : query);
    }

    /**
     * Returns normalised publisher names that have more than {@code minCount}
     * active jobs, ordered by descending job count.
     * Callers should pass {@code minCount = 5} for the Browse page filter.
     */
    public List<PublisherDTO> activePublishers(int minCount) {
        return jobRepository.findActivePublishers(minCount).stream()
                .map(p -> new PublisherDTO(p.getKey(), p.getLabel(), p.getCnt()))
                .toList();
    }

    public Job ownedJob(Long id, User user) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (job.getPostedBy() == null || !job.getPostedBy().getId().equals(user.getId())) {
            throw new ValidationException("You do not own this job");
        }
        return job;
    }

    /**
     * Applies a {@link JobDTO.JobRequest} onto a {@link Job} entity.
     * MapStruct handles all scalar property copies; this method covers only the
     * fields that require domain-specific transformation logic.
     */
    private Job apply(Job job, JobDTO.JobRequest request) {
        // Scalar fields delegated to MapStruct (title, companyName, description,
        // requirements, salary, jobType, experienceLevel, locationUae, linkedinUrl,
        // featured, expiresAt, immediateJoiner, remoteUae, salaryCurrency default "AED").
        jobMapper.updateEntityFromRequest(request, job);

        // ── Fields with custom transformation ────────────────────────────────
        job.setSkills(normalizeSkills(request.skills()));
        job.setVisaType(blankToNull(request.visaType()));
        job.setEmirate(blankToNull(request.emirate()));
        job.setApplyUrl(blankToNull(request.applyUrl()));
        job.setCompanyDomain(logoResolver.domain(request.applyUrl(), request.linkedinUrl(), request.companyName()));
        job.setCompanyLogoUrl(logoResolver.resolve(request.applyUrl(), request.linkedinUrl(), request.companyName()));

        String requestedCategory = blankToNull(request.jobCategory());
        if (JobCategoryClassifier.isValid(requestedCategory)) {
            job.setJobCategory(requestedCategory);
        } else {
            String inferred = JobCategoryClassifier.classify(request.title(), request.skills());
            job.setJobCategory(inferred != null ? inferred : JobCategoryClassifier.OTHER);
        }

        if (job.getLastSeenAt() == null) {
            job.setLastSeenAt(OffsetDateTime.now());
        }
        return job;
    }

    private String uniqueSlug(String title) {
        String base = SlugGenerator.from(title);
        String slug = base;
        int index = 1;
        while (jobRepository.existsBySlug(slug)) {
            slug = base + "-" + index++;
        }
        return slug;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Merge description + requirements into a single block so the formatter
     * produces one cohesive HTML output covering both sections. Either side
     * may be null/blank without losing the other.
     */
    private String combineForFormatting(String description, String requirements) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            sb.append(description.trim());
        }
        if (requirements != null && !requirements.isBlank()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Requirements\n").append(requirements.trim());
        }
        return sb.toString();
    }

    /**
     * Accepts either a JSON array string ("[\"react\",\"java\"]") or a
     * comma-separated plain string ("react, java") and always returns a
     * valid JSON array string so the jsonb column is satisfied.
     * <p>
     * Jackson handles all escaping, so hand-rolled quote concatenation is
     * avoided and injection of special characters is not possible.
     */
    private String normalizeSkills(String skills) {
        if (skills == null || skills.isBlank()) return "[]";
        String trimmed = skills.trim();
        if (trimmed.startsWith("[")) {
            // Already a JSON array — trust it as-is
            return trimmed;
        }
        // Plain comma-separated — let Jackson build the JSON array
        List<String> tokens = Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        try {
            return objectMapper.writeValueAsString(tokens);
        } catch (JsonProcessingException e) {
            log.warn("normalizeSkills serialization failed, falling back to empty array", e);
            return "[]";
        }
    }
}
