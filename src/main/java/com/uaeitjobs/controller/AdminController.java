package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.dto.ExternalIngestRequest;
import com.uaeitjobs.entity.IngestRunLog;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.IngestRunLogRepository;
import com.uaeitjobs.service.AdminService;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.DemoJobSeedService;
import com.uaeitjobs.service.ingest.IngestedJob;
import com.uaeitjobs.service.ingest.JobIngestService;
import com.uaeitjobs.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.service.JobService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final DemoJobSeedService demoJobSeedService;
    private final CurrentUserService currentUserService;
    private final JobIngestService jobIngestService;
    private final IngestRunLogRepository ingestRunLogRepository;
    private final JobService jobService;

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

    /** Resend the account-activation email for a user who hasn't verified yet. */
    @PostMapping("/users/{id}/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@PathVariable Long id) {
        adminService.resendVerification(id);
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
     * Accept jobs from external scrapers (Bayt, NaukriGulf, Playwright scripts, etc.)
     * and run them through the standard ingest pipeline (dedup + scoring).
     * Responds synchronously with per-run counters.
     */
    @PostMapping("/ingest/external")
    public Map<String, Object> ingestExternal(@RequestBody ExternalIngestRequest req) {
        if (req.getJobs() == null || req.getJobs().isEmpty()) {
            return Map.of("source", req.getSource(), "fetched", 0, "inserted", 0, "duplicates", 0, "rejected", 0);
        }
        String src = req.getSource() != null && !req.getSource().isBlank() ? req.getSource() : "external";
        List<IngestedJob> jobs = req.getJobs().stream()
                .filter(j -> j.getExternalId() != null && j.getTitle() != null && j.getApplyUrl() != null)
                .map(j -> new IngestedJob(
                        src + "_" + j.getExternalId(),
                        src,
                        j.getPublisher() != null ? j.getPublisher() : src,
                        j.getTitle(),
                        j.getCompany() != null ? j.getCompany() : "Unknown",
                        j.getDescription() != null ? j.getDescription() : j.getTitle(),
                        null,
                        j.getLocation() != null ? j.getLocation() : "United Arab Emirates, AE",
                        j.getEmirate(),
                        j.getSalaryMin(),
                        j.getSalaryMax(),
                        j.getSalaryCurrency() != null ? j.getSalaryCurrency() : "AED",
                        j.getJobType() != null ? j.getJobType() : "full_time",
                        inferExperienceLevel(j.getTitle()),
                        j.getApplyUrl(),
                        Boolean.TRUE.equals(j.getRemoteUae()),
                        parseDate(j.getPostedAt())
                ))
                .collect(Collectors.toList());

        JobIngestService.Counters c = jobIngestService.runExternalBatch(jobs, src);
        return Map.of(
                "source",     src,
                "fetched",    c.fetched,
                "inserted",   c.inserted,
                "duplicates", c.duplicatesL1 + c.duplicatesL2 + c.duplicatesL3,
                "rejected",   c.rejectedHard + c.rejectedScore
        );
    }

    private static String inferExperienceLevel(String title) {
        if (title == null) return "mid_3_5_yrs";
        String t = title.toLowerCase();
        if (t.contains("senior") || t.contains("lead") || t.contains("principal") || t.contains("staff")) return "senior_5_plus";
        if (t.contains("junior") || t.contains("entry") || t.contains("graduate")) return "junior_1_2_yrs";
        if (t.contains("intern")) return "fresher";
        return "mid_3_5_yrs";
    }

    private static OffsetDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.substring(0, 10)).atStartOfDay().atOffset(ZoneOffset.UTC); }
        catch (Exception e) { return null; }
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

    /**
     * Admin job listing — includes both active and archived jobs.
     * Supports searching by title/company and filtering by active status.
     */
    @GetMapping("/jobs")
    public Page<JobDTO.JobResponse> adminJobs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return jobService.adminList(q, active, PageUtil.page(page, size));
    }
}
