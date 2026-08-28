package com.uaeitjobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.dto.JobSeekerProfileDTO;
import com.uaeitjobs.dto.SavedJobDTO;
import com.uaeitjobs.dto.SavedSearchDTO;
import com.uaeitjobs.dto.SeekerAiDTO;
import com.uaeitjobs.entity.*;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.mapper.JobMapper;
import com.uaeitjobs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSeekerService {
    private static final Set<String> VALID_AI_PROVIDERS = Set.of("openai", "claude", "gemini");
    private static final HttpClient AI_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final JobSeekerProfileRepository profileRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public JobSeekerProfileDTO.Response upsertProfile(User user, JobSeekerProfileDTO.Request request) {
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            JobSeekerProfile created = new JobSeekerProfile();
            created.setUser(user);
            return created;
        });
        // cvUrl is deliberately NOT settable here — it's an opaque, server-
        // generated filename written only by uploadCv(), never client input.
        profile.setHeadline(request.headline());
        profile.setSummary(request.summary());
        profile.setYearsExperience(request.yearsExperience());
        profile.setVisaStatus(request.visaStatus());
        profile.setSkills(skillsToJson(request.skills()));
        profile.setExperience(request.experience() != null ? request.experience() : "");
        profile.setEducation(request.education() != null ? request.education() : "");
        return toResponse(profileRepository.save(profile));
    }

    public JobSeekerProfileDTO.Response getProfile(User user) {
        return profileRepository.findByUser(user).map(this::toResponse).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    @Transactional
    public JobSeekerProfileDTO.Response uploadCv(User user, MultipartFile file) {
        String filename = fileStorageService.storeCv(user.getId(), file);
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            JobSeekerProfile created = new JobSeekerProfile();
            created.setUser(user);
            return created;
        });
        profile.setCvUrl(filename);
        return toResponse(profileRepository.save(profile));
    }

    /** Streams the current user's own CV. */
    @Transactional(readOnly = true)
    public Path resolveOwnCv(User user) {
        JobSeekerProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No CV on file"));
        if (profile.getCvUrl() == null || profile.getCvUrl().isBlank()) {
            throw new ResourceNotFoundException("No CV on file");
        }
        return fileStorageService.resolveCv(user.getId(), profile.getCvUrl());
    }

    @Transactional
    public JobSeekerProfileDTO.Response updateSkills(User user, String skills) {
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        profile.setSkills(skillsToJson(skills));
        return toResponse(profile);
    }

    /**
     * Idempotent apply-click tracker for external jobs.
     * Records the application if one doesn't exist yet — silently no-ops if it does.
     * No cover letter, no duplicate error — just a click-time bookmark that
     * populates the applied/available sections on the Browse page.
     */
    @Transactional
    public void trackApply(User user, Long jobId) {
        Job job = jobRepository.findByIdAndActiveTrue(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (applicationRepository.existsByJobAndUser(job, user)) return; // already tracked
        ApplicationEntity application = new ApplicationEntity();
        application.setJob(job);
        application.setUser(user);
        applicationRepository.save(application);
    }

    @Transactional
    public ApplicationDTO.Response apply(User user, ApplicationDTO.Request request) {
        Job job = jobRepository.findByIdAndActiveTrue(request.jobId()).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (applicationRepository.existsByJobAndUser(job, user)) {
            throw new ValidationException("Already applied to this job");
        }
        ApplicationEntity application = new ApplicationEntity();
        application.setJob(job);
        application.setUser(user);
        application.setCoverLetter(request.coverLetter());
        ApplicationEntity saved = applicationRepository.save(application);
        emailService.sendJobApplicationConfirmation(user.getEmail(), job.getTitle(), job.getCompanyName());
        if (job.getPostedBy() != null) {
            emailService.notifyNewApplicant(job.getPostedBy().getEmail(), job.getTitle(), applicantName(user), user.getEmail());
        }
        return applicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDTO.Response> applications(User user, Pageable pageable) {
        // ApplicationEntity uses 'appliedAt', not 'createdAt' — override the default sort
        Pageable byAppliedAt = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "appliedAt"));
        return applicationRepository.findByUser(user, byAppliedAt).map(applicationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Set<Long> appliedJobIds(User user) {
        return applicationRepository.findJobIdsByUser(user);
    }

    @Transactional(readOnly = true)
    public List<SavedJobDTO.Response> savedJobs(User user) {
        return savedJobRepository.findTop500ByUserOrderBySavedAtDesc(user).stream()
                .map(s -> new SavedJobDTO.Response(s.getId(), jobMapper.toResponse(s.getJob()), s.getSavedAt()))
                .toList();
    }

    @Transactional
    public void saveJob(User user, Long jobId) {
        Job job = jobRepository.findByIdAndActiveTrue(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!savedJobRepository.existsByUserAndJob(user, job)) {
            SavedJob savedJob = new SavedJob();
            savedJob.setUser(user);
            savedJob.setJob(job);
            savedJobRepository.save(savedJob);
        }
    }

    @Transactional
    public void unsaveJob(User user, Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        savedJobRepository.deleteByUserAndJob(user, job);
    }

    // ── Saved searches — a named filter combo the seeker can re-apply later.
    // "filters" is stored exactly as the Browse page's own URL query string,
    // so re-applying one is just navigating to /jobs?{filters} — no separate
    // parsing/serialisation format to keep in sync with the filter bar.

    @Transactional(readOnly = true)
    public List<SavedSearchDTO.Response> savedSearches(User user) {
        return savedSearchRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(s -> new SavedSearchDTO.Response(s.getId(), s.getName(), s.getFilters(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public SavedSearchDTO.Response saveSearch(User user, SavedSearchDTO.Request request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("name is required");
        }
        if (request.filters() == null || request.filters().isBlank()) {
            throw new ValidationException("filters is required");
        }
        SavedSearch search = new SavedSearch();
        search.setUser(user);
        search.setName(request.name().trim());
        search.setFilters(request.filters());
        SavedSearch saved = savedSearchRepository.save(search);
        return new SavedSearchDTO.Response(saved.getId(), saved.getName(), saved.getFilters(), saved.getCreatedAt());
    }

    @Transactional
    public SavedSearchDTO.Response updateSearch(User user, Long id, SavedSearchDTO.Request request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("name is required");
        }
        if (request.filters() == null || request.filters().isBlank()) {
            throw new ValidationException("filters is required");
        }
        SavedSearch search = savedSearchRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Saved search not found"));
        search.setName(request.name().trim());
        search.setFilters(request.filters());
        return new SavedSearchDTO.Response(search.getId(), search.getName(), search.getFilters(), search.getCreatedAt());
    }

    @Transactional
    public void deleteSearch(User user, Long id) {
        SavedSearch search = savedSearchRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Saved search not found"));
        savedSearchRepository.delete(search);
    }

    // ── AI-drafted cover letters ────────────────────────────────────────────
    // The seeker supplies their own provider API key — they pay for their
    // own AI usage, we never absorb the cost or see the key in plaintext
    // after save. Drafting is the whole feature: the seeker reviews and
    // edits before submitting, we never auto-submit on their behalf. See
    // uaeitjobs-web-app session notes for why full autonomous submission on
    // external sites (LinkedIn, Bayt, ...) is out of scope — it would need
    // the seeker's own logged-in session there, not just an API key, and
    // risks their account being flagged for automation.

    @Transactional
    public SeekerAiDTO.SettingsResponse saveAiSettings(User user, SeekerAiDTO.SettingsRequest request) {
        String provider = request.provider().toLowerCase().trim();
        if (!VALID_AI_PROVIDERS.contains(provider)) {
            throw new ValidationException("Unknown AI provider — use openai, claude, or gemini");
        }
        if (request.apiKey().isBlank()) {
            throw new ValidationException("API key cannot be blank");
        }
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            JobSeekerProfile created = new JobSeekerProfile();
            created.setUser(user);
            return created;
        });
        profile.setAiProvider(provider);
        profile.setAiApiKeyEncrypted(encryptionService.encrypt(request.apiKey().trim()));
        profileRepository.save(profile);
        return aiSettingsResponse(profile);
    }

    @Transactional(readOnly = true)
    public SeekerAiDTO.SettingsResponse getAiSettings(User user) {
        return profileRepository.findByUser(user).map(this::aiSettingsResponse)
                .orElse(new SeekerAiDTO.SettingsResponse(null, false, null));
    }

    @Transactional
    public void deleteAiSettings(User user) {
        profileRepository.findByUser(user).ifPresent(profile -> {
            profile.setAiProvider(null);
            profile.setAiApiKeyEncrypted(null);
            profileRepository.save(profile);
        });
    }

    // Deliberately NOT @Transactional: draftCoverLetter makes a synchronous,
    // user-initiated outbound call to a third-party LLM (up to 30s, see
    // AI_HTTP_CLIENT/sendAndParse below). Wrapping that in a transaction would
    // hold a HikariCP connection for the full duration of someone else's API —
    // a handful of concurrent drafts would exhaust the pool for everyone. The
    // two repository reads below each run in their own short-lived transaction
    // (Spring Data JPA's default per-method behavior) and every field touched
    // afterwards (profile/job getters used in buildCoverLetterPrompt) is a
    // simple column, not a lazy association, so nothing here needs the
    // read to stay open past the initial fetch.
    public SeekerAiDTO.DraftResponse draftCoverLetter(User user, Long jobId) {
        JobSeekerProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new ValidationException("Complete your profile before drafting a cover letter"));
        if (profile.getAiApiKeyEncrypted() == null || profile.getAiProvider() == null) {
            throw new ValidationException("Add your AI provider and API key in your profile first");
        }
        Job job = jobRepository.findByIdAndActiveTrue(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        String apiKey = encryptionService.decrypt(profile.getAiApiKeyEncrypted());
        String systemPrompt =
                "You write concise, specific cover letters for job applications. " +
                "3-4 short paragraphs, no placeholders like [Company Name], no generic filler. " +
                "Reference at least one concrete detail from the candidate's background and one " +
                "from the job description. Plain text only, no markdown formatting.";
        String userPrompt = buildCoverLetterPrompt(profile, job);

        String text;
        try {
            text = switch (profile.getAiProvider()) {
                case "openai" -> callOpenAi(apiKey, systemPrompt, userPrompt);
                case "claude" -> callClaude(apiKey, systemPrompt, userPrompt);
                case "gemini" -> callGemini(apiKey, systemPrompt, userPrompt);
                default -> throw new ValidationException("Unknown AI provider");
            };
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            log.warn("AI cover letter draft failed for user {} provider {}: {}", user.getId(), profile.getAiProvider(), e.getMessage());
            throw new ValidationException("Couldn't reach " + profile.getAiProvider() + " — check your API key and try again.");
        }
        return new SeekerAiDTO.DraftResponse(text.trim());
    }

    private SeekerAiDTO.SettingsResponse aiSettingsResponse(JobSeekerProfile profile) {
        boolean configured = profile.getAiApiKeyEncrypted() != null;
        String masked = null;
        if (configured) {
            try {
                String raw = encryptionService.decrypt(profile.getAiApiKeyEncrypted());
                masked = raw.length() > 4
                        ? "•".repeat(Math.min(raw.length() - 4, 20)) + raw.substring(raw.length() - 4)
                        : "••••";
            } catch (RuntimeException e) {
                masked = "••••"; // decryption key rotated — key is unusable but don't leak that as an error here
            }
        }
        return new SeekerAiDTO.SettingsResponse(profile.getAiProvider(), configured, masked);
    }

    private String buildCoverLetterPrompt(JobSeekerProfile profile, Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job: ").append(job.getTitle()).append(" at ").append(job.getCompanyName()).append('\n');
        if (job.getLocationUae() != null) sb.append("Location: ").append(job.getLocationUae()).append('\n');
        sb.append("Job description:\n").append(truncate(job.getDescription(), 3000)).append("\n\n");

        sb.append("Candidate background:\n");
        if (profile.getHeadline() != null && !profile.getHeadline().isBlank()) sb.append("Headline: ").append(profile.getHeadline()).append('\n');
        if (profile.getYearsExperience() != null) sb.append("Years of experience: ").append(profile.getYearsExperience()).append('\n');
        if (profile.getSummary() != null && !profile.getSummary().isBlank()) sb.append("Summary: ").append(profile.getSummary()).append('\n');
        if (profile.getSkills() != null && !"[]".equals(profile.getSkills())) sb.append("Skills: ").append(profile.getSkills()).append('\n');
        if (profile.getExperience() != null && !profile.getExperience().isBlank()) sb.append("Experience:\n").append(truncate(profile.getExperience(), 2000)).append('\n');
        if (profile.getEducation() != null && !profile.getEducation().isBlank()) sb.append("Education:\n").append(truncate(profile.getEducation(), 1000)).append('\n');

        sb.append("\nWrite the cover letter now.");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // Deliberately minimal adapters — full retry/backoff/streaming isn't
    // warranted for a synchronous, user-initiated, one-shot draft request.
    // Mirrors the request/response shapes in service.ingest.pipeline.description.llm,
    // but those clients are bound to one app-wide config-injected key and
    // aren't reusable here where each call carries a different user's key.

    private String callOpenAi(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.6,
                "max_tokens", 700
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        JsonNode root = sendAndParse(request, "OpenAI");
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) throw new ValidationException("OpenAI returned an empty response");
        return text;
    }

    private String callClaude(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", "claude-3-5-haiku-20241022",
                "max_tokens", 700,
                "temperature", 0.6,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        JsonNode root = sendAndParse(request, "Claude");
        String text = root.path("content").path(0).path("text").asText("");
        if (text.isBlank()) throw new ValidationException("Claude returned an empty response");
        return text;
    }

    private String callGemini(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of("temperature", 0.6, "maxOutputTokens", 700, "candidateCount", 1)
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        JsonNode root = sendAndParse(request, "Gemini");
        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) throw new ValidationException("Gemini returned an empty response");
        return text;
    }

    private JsonNode sendAndParse(HttpRequest request, String providerLabel) throws Exception {
        HttpResponse<byte[]> response = AI_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String responseBody = new String(response.body(), StandardCharsets.UTF_8);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ValidationException(providerLabel + " rejected the API key (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() == 429) {
            throw new ValidationException(providerLabel + " rate limit reached — try again shortly");
        }
        if (response.statusCode() >= 400) {
            log.warn("{} request failed with HTTP {}: {}", providerLabel, response.statusCode(), truncate(responseBody, 500));
            throw new ValidationException(providerLabel + " request failed (HTTP " + response.statusCode()
                    + "). Check your API key and model access, then try again.");
        }
        if (responseBody.isBlank()) {
            throw new ValidationException(providerLabel + " returned an empty response");
        }
        return objectMapper.readTree(responseBody);
    }

    private JobSeekerProfileDTO.Response toResponse(JobSeekerProfile profile) {
        // profile.getCvUrl() is an opaque server-generated filename now, never
        // exposed directly — the client gets the fixed download endpoint
        // instead, or null when there's nothing to download.
        boolean hasCv = profile.getCvUrl() != null && !profile.getCvUrl().isBlank();
        // Relative to the API base (no /api/v1 prefix) — matches every other
        // path this service hands to the frontend's axios instance.
        String cvDownloadUrl = hasCv ? "/job-seeker/cv" : null;
        return new JobSeekerProfileDTO.Response(profile.getId(), cvDownloadUrl, profile.getHeadline(), profile.getSummary(), profile.getYearsExperience(), profile.getVisaStatus(), profile.getSkills(), profile.getExperience(), profile.getEducation());
    }

    /**
     * Converts a user-supplied skills string into a valid JSONB array.
     * Accepts either a pre-formed JSON array ("[\"Java\",\"React\"]") or a
     * plain comma-separated list ("Java, React, AWS") and normalises both
     * to a proper JSON array so the JSONB column never rejects the value.
     */
    private String skillsToJson(String value) {
        if (value == null || value.isBlank()) return "[]";
        String v = value.trim();
        if (v.startsWith("[")) return v;  // already a JSON array — pass through
        // Plain comma-separated text → ["Java","React","AWS"]
        String items = Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(","));
        return "[" + items + "]";
    }

    private String applicantName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        String email = user.getEmail();
        int at = email == null ? -1 : email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
