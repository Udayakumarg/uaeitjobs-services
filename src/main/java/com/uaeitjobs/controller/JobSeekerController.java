package com.uaeitjobs.controller;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.dto.JobSeekerProfileDTO;
import com.uaeitjobs.dto.SavedJobDTO;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.JobSeekerService;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobSeekerController {
    private final JobSeekerService jobSeekerService;
    private final CurrentUserService currentUserService;

    @PostMapping("/job-seeker/profile")
    public JobSeekerProfileDTO.Response upsertProfile(@RequestBody JobSeekerProfileDTO.Request request) {
        return jobSeekerService.upsertProfile(currentUserService.get(), request);
    }

    @GetMapping("/job-seeker/profile")
    public JobSeekerProfileDTO.Response profile() {
        return jobSeekerService.getProfile(currentUserService.get());
    }

    @PostMapping("/job-seeker/cv")
    public JobSeekerProfileDTO.Response uploadCv(@RequestParam("file") MultipartFile file) {
        return jobSeekerService.uploadCv(currentUserService.get(), file);
    }

    @PatchMapping("/job-seeker/skills")
    public JobSeekerProfileDTO.Response updateSkills(@RequestBody JobSeekerProfileDTO.SkillsRequest request) {
        return jobSeekerService.updateSkills(currentUserService.get(), request.skills());
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDTO.Response apply(@Valid @RequestBody ApplicationDTO.Request request) {
        return jobSeekerService.apply(currentUserService.get(), request);
    }

    @GetMapping("/applications")
    public Page<ApplicationDTO.Response> applications(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return jobSeekerService.applications(currentUserService.get(), PageUtil.page(page, size));
    }

    @GetMapping("/saved-jobs")
    public List<SavedJobDTO.Response> savedJobs() {
        return jobSeekerService.savedJobs(currentUserService.get());
    }

    @PostMapping("/saved-jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveJob(@PathVariable Long jobId) {
        jobSeekerService.saveJob(currentUserService.get(), jobId);
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsaveJob(@PathVariable Long jobId) {
        jobSeekerService.unsaveJob(currentUserService.get(), jobId);
    }
}
