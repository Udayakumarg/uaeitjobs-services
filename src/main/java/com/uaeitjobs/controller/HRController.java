package com.uaeitjobs.controller;

import com.uaeitjobs.dto.*;
import com.uaeitjobs.service.*;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HRController {
    private final HRService hrService;
    private final JobService jobService;
    private final SubscriptionService subscriptionService;
    private final CurrentUserService currentUserService;

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
        return hrService.importLinkedIn(currentUserService.get(), request.url());
    }

    @GetMapping("/subscriptions/current")
    public SubscriptionDTO.Response currentSubscription() {
        return subscriptionService.current(currentUserService.get());
    }

    @PostMapping("/subscriptions/upgrade")
    public SubscriptionDTO.Response upgrade(@Valid @RequestBody SubscriptionDTO.UpgradeRequest request) {
        return subscriptionService.upgrade(currentUserService.get(), request.tier());
    }

    public record LinkedInImportRequest(@NotBlank String url) {
    }
}
