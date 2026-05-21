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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HRService {
    private final HRProfileRepository profileRepository;
    private final ApplicationRepository applicationRepository;
    private final LinkedInImportRepository linkedInImportRepository;
    private final JobService jobService;
    private final LinkedInScraperService linkedInScraperService;
    private final ApplicationMapper applicationMapper;

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

    public Page<ApplicationDTO.Response> applicants(Long jobId, User user, Pageable pageable) {
        Job job = jobService.ownedJob(jobId, user);
        return applicationRepository.findByJob(job, pageable).map(applicationMapper::toResponse);
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

    @Transactional
    public JobDTO.JobResponse importLinkedIn(User user, String linkedInUrl) {
        LinkedInImport importRecord = new LinkedInImport();
        importRecord.setUser(user);
        importRecord.setLinkedinJobUrl(linkedInUrl);
        importRecord = linkedInImportRepository.save(importRecord);
        try {
            LinkedInJobData scraped = linkedInScraperService.scrapeLinkedInJob(linkedInUrl);
            SalaryRange salaryRange = parseSalary(scraped.getSalary());
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
            log.info("LinkedIn job imported: {} by user {}", response.title(), user.getId());
            return response;
        } catch (RuntimeException ex) {
            importRecord.setStatus("failed");
            importRecord.setErrorMessage(ex.getMessage());
            importRecord.setProcessedAt(OffsetDateTime.now());
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
