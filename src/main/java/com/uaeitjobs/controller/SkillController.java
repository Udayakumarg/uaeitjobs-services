package com.uaeitjobs.controller;

import com.uaeitjobs.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkillController {
    private final JobService jobService;

    @GetMapping("/skills/autocomplete")
    public List<String> autocomplete(@RequestParam(defaultValue = "") String q) {
        return jobService.skills(q);
    }

    @GetMapping("/locations")
    public List<String> locations() {
        return List.of("Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain", "Al Ain");
    }
}
