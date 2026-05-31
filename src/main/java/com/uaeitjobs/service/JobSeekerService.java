package com.uaeitjobs.service;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.dto.JobSeekerProfileDTO;
import com.uaeitjobs.dto.SavedJobDTO;
import com.uaeitjobs.entity.*;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.mapper.JobMapper;
import com.uaeitjobs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobSeekerService {
    private final JobSeekerProfileRepository profileRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;

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
        profile.setSkills(skillsToJson(skills));
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
        return savedJobRepository.findByUser(user).stream()
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

    private JobSeekerProfileDTO.Response toResponse(JobSeekerProfile profile) {
        return new JobSeekerProfileDTO.Response(profile.getId(), profile.getCvUrl(), profile.getHeadline(), profile.getSummary(), profile.getYearsExperience(), profile.getVisaStatus(), profile.getSkills(), profile.getExperience(), profile.getEducation());
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
