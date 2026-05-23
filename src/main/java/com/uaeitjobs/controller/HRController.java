package com.uaeitjobs.controller;

import com.uaeitjobs.dto.*;
import com.uaeitjobs.service.*;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HRController {
    private final HRService hrService;
    private final JobService jobService;
    private final SubscriptionService subscriptionService;
    private final CurrentUserService currentUserService;
    private final UrlJobScraperService urlJobScraperService;
    private final LinkedInScraperService linkedInScraperService;

    private static final Pattern LINKEDIN_JOB_URL =
            Pattern.compile("https?://([a-z]{2,3}\\.)?linkedin\\.com/jobs/view/.*", Pattern.CASE_INSENSITIVE);

    @PostMapping("/hr/profile")
    public HRProfileDTO.Response upsertProfile(@Valid @RequestBody HRProfileDTO.Request request) {
        return hrService.upsertProfile(currentUserService.get(), request);
    }

    @GetMapping("/hr/profile")
    public HRProfileDTO.Response profile() {
        return hrService.getProfile(currentUserService.get());
    }

    @GetMapping("/hr/jobs")
    public Page<JobDTO.JobResponse> myJobs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return jobService.postedBy(currentUserService.get(), PageUtil.page(page, size));
    }

    @GetMapping("/hr/jobs/{id}/applicants")
    public Page<ApplicationDTO.Response> applicants(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return hrService.applicants(id, currentUserService.get(), PageUtil.page(page, size));
    }

    @PatchMapping("/applications/{id}")
    public ApplicationDTO.Response updateApplication(@PathVariable Long id, @Valid @RequestBody ApplicationDTO.StatusRequest request) {
        return hrService.updateApplicationStatus(id, currentUserService.get(), request.status());
    }

    @PostMapping("/linkedin-import")
    public JobDTO.JobResponse importLinkedIn(@Valid @RequestBody LinkedInImportRequest request) {
        return hrService.importLinkedIn(currentUserService.get(), request.linkedInUrl());
    }

    /**
     * Preview-only endpoint: fetches the job URL and extracts whatever data is
     * available.  Returns a {@link UrlImportDTO.Preview} — never saves anything.
     *
     * <ul>
     *   <li>LinkedIn job URLs go through the existing {@link LinkedInScraperService}
     *       which knows LinkedIn's HTML selectors.</li>
     *   <li>All other URLs go through the generic {@link UrlJobScraperService}
     *       which tries JSON-LD → og:* meta → title tag in that order.</li>
     * </ul>
     *
     * The caller reviews the preview, fills in any missing fields (especially
     * the description when the page is JS-rendered), and then POSTs to
     * {@code /api/v1/jobs} to persist the final job.
     */
    @PostMapping("/hr/jobs/import-preview")
    public UrlImportDTO.Preview importPreview(@Valid @RequestBody UrlImportDTO.Request request) {
        String url = request.url().strip();

        if (LINKEDIN_JOB_URL.matcher(url).matches()) {
            // LinkedIn: reuse the existing scraper that knows LinkedIn's HTML structure
            LinkedInJobData ld = linkedInScraperService.scrapeLinkedInJob(url);
            return UrlImportDTO.Preview.builder()
                    .title(ld.getTitle())
                    .companyName(ld.getCompanyName())
                    .description(ld.getDescription())
                    .applyUrl(url)
                    .complete(true)
                    .build();
        }

        return urlJobScraperService.scrape(url);
    }

    @GetMapping("/subscriptions/current")
    public SubscriptionDTO.Response currentSubscription() {
        return subscriptionService.current(currentUserService.get());
    }

    @PostMapping("/subscriptions/upgrade")
    public SubscriptionDTO.Response upgrade(@Valid @RequestBody SubscriptionDTO.UpgradeRequest request) {
        return subscriptionService.upgrade(currentUserService.get(), request.tier());
    }

    public record LinkedInImportRequest(@NotBlank(message = "LinkedIn URL is required") String linkedInUrl) {
    }
}
