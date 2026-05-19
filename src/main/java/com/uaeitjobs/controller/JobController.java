package com.uaeitjobs.controller;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.dto.StatsDTO;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.JobService;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    private final CurrentUserService currentUserService;

    @GetMapping("/jobs")
    public Page<JobDTO.JobResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String type, @RequestParam(required = false) String level,
                                         @RequestParam(required = false) String location, @RequestParam(required = false) String skills,
                                         @RequestParam(required = false) String visaType,
                                         @RequestParam(required = false) String emirate,
                                         @RequestParam(required = false) Boolean immediateJoiner,
                                         @RequestParam(required = false) Boolean remoteUae) {
        boolean anyFilter = type != null || level != null || location != null || skills != null
                || visaType != null || emirate != null || immediateJoiner != null || remoteUae != null;
        if (anyFilter) {
            return jobService.filter(type, level, location, skills, visaType, emirate, immediateJoiner, remoteUae, PageUtil.page(page, size));
        }
        return jobService.list(PageUtil.page(page, size));
    }

    @GetMapping("/jobs/{id}")
    public JobDTO.JobResponse detail(@PathVariable Long id) {
        return jobService.detail(id);
    }

    @GetMapping("/jobs/search")
    public Page<JobDTO.JobResponse> search(@RequestParam String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return jobService.search(q, PageUtil.page(page, size));
    }

    @GetMapping("/jobs/filter")
    public Page<JobDTO.JobResponse> filter(@RequestParam(required = false) String type, @RequestParam(required = false) String level,
                                           @RequestParam(required = false) String location, @RequestParam(required = false) String skills,
                                           @RequestParam(required = false) String visaType,
                                           @RequestParam(required = false) String emirate,
                                           @RequestParam(required = false) Boolean immediateJoiner,
                                           @RequestParam(required = false) Boolean remoteUae,
                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return jobService.filter(type, level, location, skills, visaType, emirate, immediateJoiner, remoteUae, PageUtil.page(page, size));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDTO.JobResponse create(@Valid @RequestBody JobDTO.JobRequest request) {
        return jobService.create(request, currentUserService.get());
    }

    @PatchMapping("/jobs/{id}")
    public JobDTO.JobResponse update(@PathVariable Long id, @Valid @RequestBody JobDTO.JobRequest request) {
        return jobService.update(id, request, currentUserService.get());
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        jobService.softDelete(id, currentUserService.get());
    }

    @GetMapping("/stats")
    public StatsDTO stats() {
        return jobService.stats();
    }
}
