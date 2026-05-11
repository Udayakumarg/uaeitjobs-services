package com.uaeitjobs.controller;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.service.AdminService;
import com.uaeitjobs.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminDTO.StatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    public Page<?> users(@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.users(search, PageUtil.page(page, size));
    }

    @PatchMapping("/jobs/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean active) {
        adminService.setJobActive(id, active);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }
}
