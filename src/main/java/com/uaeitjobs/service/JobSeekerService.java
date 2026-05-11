package com.uaeitjobs.service;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.dto.JobSeekerProfileDTO;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobSeekerService {
    private final JobSeekerProfileRepository profileRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationMapper applicationMapper;
    private final FileStorageService fileStorageService;

    @Transactional
    public JobSeekerProfileDTO.Response upsertProfile(User user, JobSeekerProfileDTO.Request request) {
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            JobSeekerProfile created = new JobSeekerProfile();
            created.setUser(user);
            return created;
        });
        profile.setCvUrl(request.cvUrl());
        profile.setHeadline(request.headline());
        profile.setSummary(request.summary());
        profile.setYearsExperience(request.yearsExperience());
        profile.setVisaStatus(request.visaStatus());
        profile.setSkills(defaultJson(request.skills()));
        profile.setExperience(defaultJson(request.experience()));
        profile.setEducation(defaultJson(request.education()));
        return toResponse(profileRepository.save(profile));
    }

    public JobSeekerProfileDTO.Response getProfile(User user) {
        return profileRepository.findByUser(user).map(this::toResponse).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    @Transactional
    public JobSeekerProfileDTO.Response uploadCv(User user, MultipartFile file) {
        String url = fileStorageService.storeCv(user.getId(), file);
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseGet(() -> {
            JobSeekerProfile created = new JobSeekerProfile();
            created.setUser(user);
            return created;
        });
        profile.setCvUrl(url);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public JobSeekerProfileDTO.Response updateSkills(User user, String skills) {
        JobSeekerProfile profile = profileRepository.findByUser(user).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        profile.setSkills(defaultJson(skills));
        return toResponse(profile);
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
        return applicationMapper.toResponse(applicationRepository.save(application));
    }

    public Page<ApplicationDTO.Response> applications(User user, Pageable pageable) {
        return applicationRepository.findByUser(user, pageable).map(applicationMapper::toResponse);
    }

    public List<SavedJob> savedJobs(User user) {
        return savedJobRepository.findByUser(user);
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

    private JobSeekerProfileDTO.Response toResponse(JobSeekerProfile profile) {
        return new JobSeekerProfileDTO.Response(profile.getId(), profile.getCvUrl(), profile.getHeadline(), profile.getSummary(), profile.getYearsExperience(), profile.getVisaStatus(), profile.getSkills(), profile.getExperience(), profile.getEducation());
    }

    private String defaultJson(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }
}
