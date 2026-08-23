package com.uaeitjobs.service;

import com.uaeitjobs.dto.*;
import com.uaeitjobs.entity.*;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HRService {
    private final HRProfileRepository profileRepository;
    private final JobSeekerProfileRepository seekerProfileRepository;
    private final ApplicationRepository applicationRepository;
    private final LinkedInImportRepository linkedInImportRepository;
    private final JobService jobService;
    private final LinkedInScraperService linkedInScraperService;
    private final ApplicationMapper applicationMapper;
    private final JobRepository jobRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public HRProfileDTO.Response upsertProfile(User user, HRProfileDTO.Request request) {
        HRProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            HRProfile created = new HRProfile();
            created.setUser(user);
            return created;
        });
        profile.setCompanyName(request.companyName());
        profile.setCompanyLogoUrl(request.companyLogoUrl());
        profile.setWebsite(request.website());
        profile.setIndustry(request.industry());
        if (request.subscriptionTier() != null) {
            profile.setSubscriptionTier(request.subscriptionTier());
        }
        return toResponse(profileRepository.save(profile));
    }

    public HRProfileDTO.Response getProfile(User user) {
        return profileRepository.findByUser(user).map(this::toResponse).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDTO.HrView> applicants(Long jobId, User user, Pageable pageable) {
        Job job = jobService.ownedJob(jobId, user);
        // ApplicationEntity uses 'appliedAt', not 'createdAt' — override the default sort
        Pageable byAppliedAt = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "appliedAt"));
        Page<ApplicationEntity> appPage = applicationRepository.findByJob(job, byAppliedAt);

        // Batch-load seeker profiles for all applicants on this page in a single query
        List<User> applicants = appPage.getContent().stream().map(ApplicationEntity::getUser).toList();
        Map<Long, JobSeekerProfile> profileMap = seekerProfileRepository.findByUserIn(applicants).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return appPage.map(app -> toHrView(app, profileMap.get(app.getUser().getId())));
    }

    private ApplicationDTO.HrView toHrView(ApplicationEntity app, JobSeekerProfile profile) {
        ApplicationDTO.Response base = applicationMapper.toResponse(app);
        // profile.getCvUrl() is an opaque server-generated filename, never
        // exposed directly — HR gets the authenticated download endpoint
        // instead (verifies job ownership again at download time).
        boolean hasCv = profile != null && profile.getCvUrl() != null && !profile.getCvUrl().isBlank();
        // Relative to the API base (no /api/v1 prefix) — matches every other
        // path this service hands to the frontend's axios instance.
        String cvDownloadUrl = hasCv ? "/hr/applications/" + app.getId() + "/cv" : null;
        return new ApplicationDTO.HrView(
                base.id(), base.job(), base.applicant(), base.coverLetter(), base.appliedAt(), base.status(),
                profile != null ? profile.getHeadline() : null,
                profile != null ? profile.getYearsExperience() : null,
                profile != null ? profile.getSkills() : null,
                profile != null ? profile.getVisaStatus() : null,
                cvDownloadUrl);
    }

    /** Resolves the applicant's CV file, after verifying the requesting HR user owns the job. */
    @Transactional(readOnly = true)
    public Path resolveApplicantCv(Long applicationId, User hrUser) {
        // Same ownership + existence check used by updateApplicationStatus —
        // one generic error for both "not found" and "not owned" avoids IDOR.
        ApplicationEntity application = applicationRepository.findByIdAndJobOwner(applicationId, hrUser)
                .orElseThrow(() -> new ValidationException("Application not found or access denied"));
        User applicant = application.getUser();
        JobSeekerProfile profile = seekerProfileRepository.findByUser(applicant)
                .orElseThrow(() -> new ResourceNotFoundException("No CV on file"));
        if (profile.getCvUrl() == null || profile.getCvUrl().isBlank()) {
            throw new ResourceNotFoundException("No CV on file");
        }
        return fileStorageService.resolveCv(applicant.getId(), profile.getCvUrl());
    }

    @Transactional
    public ApplicationDTO.Response updateApplicationStatus(Long id, User user, ApplicationStatus status) {
        // Single round-trip: verifies both existence AND ownership — prevents IDOR.
        // Generic error message avoids leaking whether the record exists at all.
        ApplicationEntity application = applicationRepository.findByIdAndJobOwner(id, user)
                .orElseThrow(() -> new ValidationException("Application not found or access denied"));
        application.setStatus(status);
        return applicationMapper.toResponse(application);
    }

    /** Matches LinkedIn job IDs in URLs like {@code .../jobs/view/4401461297}. */
    private static final Pattern LINKEDIN_JOB_ID = Pattern.compile("/jobs/view/(\\d+)");

    // Deliberately NOT @Transactional at this level: the audit row's fate must
    // not be tied to whether the scrape + job creation ultimately succeeds.
    // It used to be — the whole method (including the "failed" status write
    // in the catch block) shared one transaction, so a scrape failure rolled
    // back its own audit trail along with everything else, and the import
    // table never recorded a single failure. jobService.create() is a
    // separate bean call and keeps its own transaction regardless.
    public JobDTO.JobResponse importLinkedIn(User user, String linkedInUrl) {
        // Reject duplicates before paying for the scrape. We match on the
        // canonical /jobs/view/{id} segment so trailing tracking params
        // ("?refId=...") don't bypass the check.
        Matcher idMatcher = LINKEDIN_JOB_ID.matcher(linkedInUrl == null ? "" : linkedInUrl);
        if (idMatcher.find()) {
            String fragment = "/jobs/view/" + idMatcher.group(1);
            if (jobRepository.existsByLinkedinUrlContaining(fragment)) {
                throw new ValidationException("This LinkedIn job has already been imported");
            }
        }
        LinkedInImport importRecord = new LinkedInImport();
        importRecord.setUser(user);
        importRecord.setLinkedinJobUrl(linkedInUrl);
        importRecord = linkedInImportRepository.save(importRecord);
        try {
            LinkedInJobData scraped = linkedInScraperService.scrapeLinkedInJob(linkedInUrl);
            SalaryRange salaryRange = parseSalary(scraped.getSalary());
            // Description and requirements are passed raw; JobService.create()
            // runs them through the formatter registry to populate
            // descriptionHtml (the structured-HTML field the UI renders).
            JobDTO.JobRequest request = new JobDTO.JobRequest(
                    scraped.getTitle(),
                    scraped.getCompanyName(),
                    scraped.getDescription(),
                    scraped.getRequirements(),
                    salaryRange.min(),
                    salaryRange.max(),
                    "AED",
                    scraped.getJobType(),
                    scraped.getExperienceLevel(),
                    "Dubai",
                    toJsonArray(scraped.getSkills()),
                    scraped.getLinkedInUrl(),
                    false,
                    OffsetDateTime.now().plusDays(30),
                    null,     // visaType — unknown from LinkedIn scrape
                    null,     // emirate
                    false,    // immediateJoiner
                    false,    // remoteUae
                    null,     // jobCategory — auto-inferred from title
                    scraped.getLinkedInUrl() // applyUrl — send applicants back to LinkedIn
            );
            JobDTO.JobResponse response = jobService.create(request, user, "linkedin");
            importRecord.setStatus("processed");
            importRecord.setProcessedAt(OffsetDateTime.now());
            linkedInImportRepository.save(importRecord);
            log.info("LinkedIn job imported: {} by user {}", response.title(), user.getId());
            return response;
        } catch (RuntimeException ex) {
            importRecord.setStatus("failed");
            importRecord.setErrorMessage(ex.getMessage());
            importRecord.setProcessedAt(OffsetDateTime.now());
            linkedInImportRepository.save(importRecord);
            throw ex;
        }
    }

    private HRProfileDTO.Response toResponse(HRProfile profile) {
        return new HRProfileDTO.Response(profile.getId(), profile.getCompanyName(), profile.getCompanyLogoUrl(), profile.getWebsite(), profile.getIndustry(), profile.getSubscriptionTier());
    }

    private SalaryRange parseSalary(String salary) {
        if (salary == null || salary.isBlank()) {
            return new SalaryRange(null, null);
        }
        Matcher matcher = Pattern.compile("\\d[\\d,]*").matcher(salary);
        Integer min = matcher.find() ? parseNumber(matcher.group()) : null;
        Integer max = matcher.find() ? parseNumber(matcher.group()) : min;
        return new SalaryRange(min, max);
    }

    private Integer parseNumber(String number) {
        return Integer.valueOf(number.replace(",", ""));
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private record SalaryRange(Integer min, Integer max) {
    }
}
