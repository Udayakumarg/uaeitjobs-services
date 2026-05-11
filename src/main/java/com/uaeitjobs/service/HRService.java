package com.uaeitjobs.service;

import com.uaeitjobs.dto.*;
import com.uaeitjobs.entity.*;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

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
        ApplicationEntity application = applicationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (application.getJob().getPostedBy() == null || !application.getJob().getPostedBy().getId().equals(user.getId())) {
            throw new ValidationException("You do not own this job");
        }
        application.setStatus(status);
        return applicationMapper.toResponse(application);
    }

    @Transactional
    public JobDTO.JobResponse importLinkedIn(User user, String url) {
        LinkedInImport importRecord = new LinkedInImport();
        importRecord.setUser(user);
        importRecord.setLinkedinJobUrl(url);
        importRecord = linkedInImportRepository.save(importRecord);
        try {
            JobDTO.JobRequest request = linkedInScraperService.scrape(url);
            JobDTO.JobResponse response = jobService.create(request, user);
            importRecord.setStatus("processed");
            importRecord.setProcessedAt(OffsetDateTime.now());
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
}
