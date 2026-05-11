package com.uaeitjobs.service;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.dto.StatsDTO;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.JobMapper;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobMapper jobMapper;
    private final SubscriptionService subscriptionService;

    public Page<JobDTO.JobResponse> list(Pageable pageable) {
        return jobRepository.findByActiveTrue(pageable).map(jobMapper::toResponse);
    }

    @Transactional
    public JobDTO.JobResponse detail(Long id) {
        Job job = jobRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        jobRepository.incrementViewCount(id);
        job.setViewCount(job.getViewCount() + 1);
        return jobMapper.toResponse(job);
    }

    public Page<JobDTO.JobResponse> filter(String type, String level, String location, String skill, Pageable pageable) {
        return jobRepository.filter(blankToNull(type), blankToNull(level), blankToNull(location), blankToNull(skill), pageable).map(jobMapper::toResponse);
    }

    public Page<JobDTO.JobResponse> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return list(pageable);
        }
        return jobRepository.search(query, pageable).map(jobMapper::toResponse);
    }

    @Transactional
    public JobDTO.JobResponse create(JobDTO.JobRequest request, User user) {
        return create(request, user, "manual");
    }

    @Transactional
    public JobDTO.JobResponse create(JobDTO.JobRequest request, User user, String source) {
        if (!user.isVerified()) {
            throw new ValidationException("Verify email before posting jobs");
        }
        subscriptionService.assertCanPost(user);
        Job job = apply(new Job(), request);
        job.setSlug(uniqueSlug(request.title()));
        job.setPostedBy(user);
        job.setSource(source);
        Job saved = jobRepository.save(job);
        subscriptionService.incrementPosted(user);
        return jobMapper.toResponse(saved);
    }

    @Transactional
    public JobDTO.JobResponse update(Long id, JobDTO.JobRequest request, User user) {
        Job job = ownedJob(id, user);
        return jobMapper.toResponse(apply(job, request));
    }

    @Transactional
    public void softDelete(Long id, User user) {
        ownedJob(id, user).setActive(false);
    }

    public Page<JobDTO.JobResponse> postedBy(User user, Pageable pageable) {
        return jobRepository.findByPostedBy(user, pageable).map(jobMapper::toResponse);
    }

    public StatsDTO stats() {
        return new StatsDTO(jobRepository.countByActiveTrue(), userRepository.count(), jobRepository.countActiveCompanies());
    }

    public List<String> skills(String query) {
        return jobRepository.findSkills(query == null ? "" : query);
    }

    public Job ownedJob(Long id, User user) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (job.getPostedBy() == null || !job.getPostedBy().getId().equals(user.getId())) {
            throw new ValidationException("You do not own this job");
        }
        return job;
    }

    private Job apply(Job job, JobDTO.JobRequest request) {
        job.setTitle(request.title());
        job.setCompanyName(request.companyName());
        job.setDescription(request.description());
        job.setRequirements(request.requirements());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryCurrency(request.salaryCurrency() == null ? "AED" : request.salaryCurrency());
        job.setJobType(request.jobType());
        job.setExperienceLevel(request.experienceLevel());
        job.setLocationUae(request.locationUae());
        job.setSkills(request.skills() == null ? "[]" : request.skills());
        job.setLinkedinUrl(request.linkedinUrl());
        job.setFeatured(Boolean.TRUE.equals(request.featured()));
        job.setExpiresAt(request.expiresAt());
        return job;
    }

    private String uniqueSlug(String title) {
        String base = SlugGenerator.from(title);
        String slug = base;
        int index = 1;
        while (jobRepository.existsBySlug(slug)) {
            slug = base + "-" + index++;
        }
        return slug;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
