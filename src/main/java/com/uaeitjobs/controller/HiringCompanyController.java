package com.uaeitjobs.controller;

import com.uaeitjobs.dto.HiringCompanyDTO;
import com.uaeitjobs.service.CurrentUserService;
import com.uaeitjobs.service.HiringCompanyService;
import com.uaeitjobs.util.PageUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Public + authenticated-user endpoints for the hiring-companies directory.
 * Admin endpoints live in {@code AdminController} under /api/v1/admin/companies.
 *
 * <p>Security:
 * <ul>
 *   <li>{@code GET /api/v1/companies/**} — public (see SecurityConfig).</li>
 *   <li>{@code POST /api/v1/companies/submit} — any authenticated user.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class HiringCompanyController {

    private final HiringCompanyService service;
    private final CurrentUserService currentUserService;

    @GetMapping
    public Page<HiringCompanyDTO.Response> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "24") int size) {
        return service.publicSearch(q, city, category, PageUtil.page(page, size));
    }

    @GetMapping("/filters")
    public HiringCompanyDTO.FilterOptions filters() {
        return service.publicFilterOptions();
    }

    @GetMapping("/{slug}")
    public HiringCompanyDTO.Response detail(@PathVariable String slug) {
        return service.publicGet(slug);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public HiringCompanyDTO.Response submit(@Valid @RequestBody HiringCompanyDTO.SubmitRequest req) {
        return service.submit(currentUserService.get(), req);
    }
}
