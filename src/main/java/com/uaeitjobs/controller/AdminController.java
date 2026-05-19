package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.service.AdminService;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.DemoJobSeedService;
import com.uaeitjobs.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final DemoJobSeedService demoJobSeedService;
    private final CurrentUserService currentUserService;

    @GetMapping("/stats")
    public AdminDTO.StatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    public Page<?> users(@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.users(search, PageUtil.page(page, size));
    }

    @PatchMapping("/jobs/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean active) {
        adminService.setJobActive(id, active);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }

    /**
     * Idempotently seeds a curated catalogue of UAE-IT job postings.
     * Slugs that already exist are skipped, so the endpoint is safe to
     * call repeatedly.
     */
    @PostMapping("/seed/demo-jobs")
    public Map<String, Object> seedDemoJobs() {
        int created = demoJobSeedService.seed(currentUserService.get());
        return Map.of(
                "created", created,
                "totalTemplates", demoJobSeedService.totalTemplates()
        );
    }

    /** Deletes existing demo jobs and re-seeds from the curated catalog. */
    @PostMapping("/seed/demo-jobs/reset")
    public Map<String, Object> resetDemoJobs() {
        int purged = demoJobSeedService.purgeDemoJobs();
        int created = demoJobSeedService.seed(currentUserService.get());
        return Map.of(
                "purged", purged,
                "created", created
        );
    }
}
