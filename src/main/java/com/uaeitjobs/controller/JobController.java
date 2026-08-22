package com.uaeitjobs.controller;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.dto.PublisherDTO;
import com.uaeitjobs.dto.StatsDTO;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.JobService;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    private final CurrentUserService currentUserService;

    @GetMapping("/jobs")
    public Page<JobDTO.JobResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String type, @RequestParam(required = false) String level,
                                         @RequestParam(required = false) String location, @RequestParam(required = false) String skills,
                                         @RequestParam(required = false) String visaType,
                                         @RequestParam(required = false) String emirate,
                                         @RequestParam(required = false) Boolean immediateJoiner,
                                         @RequestParam(required = false) Boolean remoteUae,
                                         @RequestParam(required = false) String category) {
        boolean anyFilter = type != null || level != null || location != null || skills != null
                || visaType != null || emirate != null || immediateJoiner != null || remoteUae != null
                || category != null;
        if (anyFilter) {
            return jobService.filter(type, level, location, skills, visaType, emirate, immediateJoiner, remoteUae, category, PageUtil.page(page, size));
        }
        return jobService.list(PageUtil.page(page, size));
    }

    @GetMapping("/jobs/{id}")
    public JobDTO.JobResponse detail(@PathVariable Long id) {
        return jobService.detail(id);
    }

    @GetMapping("/jobs/search")
    public Page<JobDTO.JobResponse> search(@RequestParam String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return jobService.search(q, PageUtil.page(page, size));
    }

    /**
     * Multi-select filter endpoint. Each dimension (emirate, category, etc.)
     * accepts one or more values — Spring MVC collects repeated params into a
     * List automatically (e.g. {@code ?emirate=dubai&emirate=abu_dhabi}).
     * All filtering is performed in-database so no results are silently hidden
     * by the page-size cap.
     */
    /**
     * Unified search + multi-select filter.  All parameters are optional.
     * {@code q} drives a full-text search (plainto_tsquery) that is ANDed with
     * all active facet filters in a single DB query — no client-side post-filtering.
     */
    @GetMapping("/jobs/filter")
    public Page<JobDTO.JobResponse> filter(
            @RequestParam(required = false) java.util.List<String> emirate,
            @RequestParam(required = false) java.util.List<String> category,
            @RequestParam(required = false) java.util.List<String> experienceLevel,
            @RequestParam(required = false) java.util.List<String> jobType,
            @RequestParam(required = false) Boolean immediateJoiner,
            @RequestParam(required = false) Boolean remoteUae,
            @RequestParam(required = false) String postedAfter,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) java.util.List<String> publisher,
            @RequestParam(required = false) String company,
            /** "easy" (apply within the platform) or "external" (redirects to the source site). */
            @RequestParam(required = false) String applyMode,
            /** LinkedIn-specific: true = only jobs LinkedIn itself marks Easy Apply. */
            @RequestParam(required = false) Boolean linkedinEasyApply,
            @RequestParam(required = false) String visaType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "80") int size) {
        return jobService.filterMulti(emirate, category, experienceLevel, jobType,
                remoteUae, immediateJoiner, postedAfter, salaryMin, salaryMax, sort, q,
                publisher, company, applyMode, linkedinEasyApply, visaType, PageUtil.page(page, size));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDTO.JobResponse create(@Valid @RequestBody JobDTO.JobRequest request) {
        return jobService.create(request, currentUserService.get());
    }

    @PatchMapping("/jobs/{id}")
    public JobDTO.JobResponse update(@PathVariable Long id, @Valid @RequestBody JobDTO.JobRequest request) {
        return jobService.update(id, request, currentUserService.get());
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        jobService.softDelete(id, currentUserService.get());
    }

    @GetMapping("/stats")
    public StatsDTO stats() {
        return jobService.stats();
    }

    /**
     * Returns job-board publisher names that have more than {@code minCount}
     * active jobs, sorted by descending count.  The frontend uses this to
     * populate the Source filter dynamically so new job boards appear
     * automatically once they accumulate enough listings.
     *
     * @param minCount minimum job count threshold (default 5)
     */
    @GetMapping("/jobs/publishers")
    public java.util.List<PublisherDTO> publishers(
            @RequestParam(defaultValue = "5") int minCount) {
        return jobService.activePublishers(minCount);
    }
}
