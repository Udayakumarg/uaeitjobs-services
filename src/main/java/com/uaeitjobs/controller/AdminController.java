package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.dto.ExternalIngestRequest;
import com.uaeitjobs.dto.HiringCompanyDTO;
import com.uaeitjobs.dto.KeywordSuggestionDTO;
import com.uaeitjobs.entity.HiringCompanyStatus;
import com.uaeitjobs.entity.IngestRunLog;
import com.uaeitjobs.entity.KeywordSearchStrategy;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.IngestRunLogRepository;
import com.uaeitjobs.repository.KeywordSearchStrategyRepository;
import com.uaeitjobs.service.AdminService;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.DemoJobSeedService;
import com.uaeitjobs.service.HiringCompanyService;
import com.uaeitjobs.service.PlaywrightTriggerService;
import com.uaeitjobs.service.ingest.IngestedJob;
import com.uaeitjobs.service.ingest.JobIngestService;
import com.uaeitjobs.service.ingest.KeywordSuggestionService;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
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
    private final PlaywrightTriggerService playwrightTriggerService;
    private final HiringCompanyService hiringCompanyService;
    private final KeywordSearchStrategyRepository keywordSearchStrategyRepository;
    private final KeywordSuggestionService keywordSuggestionService;

    @GetMapping("/stats")
    public AdminDTO.StatsResponse stats() {
        return adminService.stats();
    }

    /** Detailed user-activity snapshot for the admin monitoring dashboard. */
    @GetMapping("/users/activity")
    public AdminDTO.UserActivityResponse userActivity() {
        return adminService.userActivity();
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

    /** Proactively detected friction signals — accounts that may need a nudge. */
    @GetMapping("/users/friction-signals")
    public List<AdminDTO.FrictionSignal> frictionSignals() {
        return adminService.frictionSignals();
    }

    /** Send a proactive welcome / onboarding email to a user. */
    @PostMapping("/users/{id}/send-welcome")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendWelcome(@PathVariable Long id) {
        adminService.sendWelcome(id);
    }

    /** Create a new user of any type (including admin) — pre-verified, no email required. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");
        // No default here on purpose — a missing userType previously fell
        // back to "admin", the single most-privileged role, so an incomplete
        // request body silently minted a full admin account instead of
        // failing loudly. Require the caller to say what they mean.
        String typeStr  = body.get("userType");
        if (typeStr == null || typeStr.isBlank()) {
            throw new com.uaeitjobs.exception.ValidationException("userType is required");
        }
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
                        parseDate(j.getPostedAt()),
                        j.getLinkedinEasyApply()
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

    /**
     * Trigger a Playwright scraper run for a specific source.
     * The request is forwarded to the Node.js trigger server running on the host.
     * Returns immediately — the scraper runs in the background.
     *
     * Valid sources: bayt | naukrigulf | gulftalent | linkedin
     */
    @PostMapping("/scraper/trigger/{source}")
    public Map<String, Object> triggerScraper(@PathVariable String source) {
        PlaywrightTriggerService.TriggerResult result = playwrightTriggerService.trigger(source);
        return Map.of(
                "status",  result.status().name().toLowerCase(),
                "message", result.message(),
                "source",  source
        );
    }

    /**
     * Returns the running/idle status of each Playwright scraper source
     * as reported by the trigger server.
     */
    @GetMapping("/scraper/status")
    public Map<String, Object> scraperStatus() {
        Map<String, String> sourceStatus = playwrightTriggerService.status();
        boolean serverReachable = !sourceStatus.isEmpty();
        return Map.of(
                "serverReachable", serverReachable,
                "sources", sourceStatus
        );
    }

    /** Every JSearch keyword with its live rotation stats — the full picture, not just what appears in recent runs. */
    @GetMapping("/keywords")
    public List<KeywordSearchStrategy> keywords() {
        return keywordSearchStrategyRepository.findAll(Sort.by(Sort.Direction.ASC, "tier", "keyword"));
    }

    /**
     * Asks the configured LLM ({@code app.llm.*}, same provider used for
     * description formatting) to propose new JSearch keywords not already
     * covered, and inserts them at the lowest rotation tier. The LLM only
     * proposes candidates — every keyword still has to earn its place via
     * the same real insert-rate tracking every other keyword goes through.
     */
    @PostMapping("/keywords/suggest")
    public KeywordSuggestionDTO.Response suggestKeywords() {
        return keywordSuggestionService.suggestAndAddKeywords();
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

    // ── Hiring-companies directory moderation ────────────────────────────
    // Backs the /admin/companies page: review pending submissions, edit any
    // field, approve / reject / delete, toggle featured / url_verified.

    /** List directory entries by moderation status (PENDING / APPROVED / REJECTED). */
    @GetMapping("/companies")
    public Page<HiringCompanyDTO.AdminResponse> adminListCompanies(
            @RequestParam(required = false) HiringCompanyStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return hiringCompanyService.adminList(status, PageUtil.page(page, size));
    }

    /**
     * Approve a submission. The request body may override any field
     * (category, city, description, etc.) atomically with the approval —
     * common workflow is "edit + approve" in a single click.
     */
    @PostMapping("/companies/{id}/approve")
    public HiringCompanyDTO.AdminResponse approveCompany(
            @PathVariable Long id,
            @RequestBody(required = false) HiringCompanyDTO.AdminPatchRequest overrides) {
        return hiringCompanyService.approve(id, currentUserService.get(), overrides);
    }

    @PostMapping("/companies/{id}/reject")
    public HiringCompanyDTO.AdminResponse rejectCompany(
            @PathVariable Long id,
            @RequestBody(required = false) HiringCompanyDTO.RejectRequest body) {
        return hiringCompanyService.reject(id, body == null ? null : body.reason());
    }

    /** Edit any field — used by the inline edit row in the moderation queue. */
    @PatchMapping("/companies/{id}")
    public HiringCompanyDTO.AdminResponse patchCompany(
            @PathVariable Long id,
            @Valid @RequestBody HiringCompanyDTO.AdminPatchRequest body) {
        return hiringCompanyService.patch(id, body);
    }

    @DeleteMapping("/companies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompany(@PathVariable Long id) {
        hiringCompanyService.delete(id);
    }
}
