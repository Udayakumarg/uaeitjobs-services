package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.entity.IngestRunLog;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.IngestRunLogRepository;
import com.uaeitjobs.service.AdminService;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.DemoJobSeedService;
import com.uaeitjobs.service.ingest.JobIngestService;
import com.uaeitjobs.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final DemoJobSeedService demoJobSeedService;
    private final CurrentUserService currentUserService;
    private final JobIngestService jobIngestService;
    private final IngestRunLogRepository ingestRunLogRepository;

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

    /** Create a new user of any type (including admin) — pre-verified, no email required. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");
        String typeStr  = body.getOrDefault("userType", "admin");
        UserType userType;
        try { userType = UserType.valueOf(typeStr); }
        catch (IllegalArgumentException e) { throw new com.uaeitjobs.exception.ValidationException("Invalid userType: " + typeStr); }
        var user = adminService.createUser(email, password, userType);
        var result = new LinkedHashMap<String, Object>();
        result.put("id",       user.getId());
        result.put("email",    user.getEmail());
        result.put("userType", user.getUserType().name());
        result.put("verified", user.isVerified());
        return result;
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

    /** Hard-delete every demo job posting. */
    @DeleteMapping("/seed/demo-jobs")
    public Map<String, Object> purgeDemoJobs() {
        int purged = demoJobSeedService.purgeDemoJobs();
        return Map.of("purged", purged);
    }

    /**
     * Asynchronously trigger one ingest pass across every enabled source.
     * Returns 202 immediately; progress is observable via /ingest/status.
     * If an ingest is already in flight the existing run is preserved and
     * the trigger is reported as a no-op.
     */
    @PostMapping("/ingest/run")
    public ResponseEntity<Map<String, Object>> runIngest() {
        if (jobIngestService.isRunning()) {
            return ResponseEntity.accepted().body(Map.of(
                    "status", "already_running",
                    "message", "Ingest already in progress — poll /ingest/status."
            ));
        }
        jobIngestService.runAllAsync();
        return ResponseEntity.accepted().body(Map.of(
                "status", "started",
                "message", "Ingest started in background — poll /ingest/status for results."
        ));
    }

    /**
     * Read-only snapshot of the ingestion state: whether a run is currently
     * active, plus the last few rows of the ingest_run_log table.
     */
    @GetMapping("/ingest/status")
    public Map<String, Object> ingestStatus(@RequestParam(defaultValue = "10") int limit) {
        int bounded = Math.max(1, Math.min(50, limit));
        List<IngestRunLog> recent = ingestRunLogRepository.findAll(
                PageRequest.of(0, bounded, Sort.by(Sort.Direction.DESC, "startedAt"))
        ).getContent();
        return Map.of(
                "running", jobIngestService.isRunning(),
                "recent", recent
        );
    }
}
