package com.uaeitjobs.service;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;

    public AdminDTO.StatsResponse stats() {
        long hr = userRepository.countByUserType(UserType.hr);
        return new AdminDTO.StatsResponse(userRepository.count(), userRepository.countByUserType(UserType.job_seeker), hr, jobRepository.count(), applicationRepository.count(), hr * 499);
    }

    public Page<?> users(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByEmailContainingIgnoreCase(search, pageable);
    }

    @Transactional
    public void setJobActive(Long id, boolean active) {
        jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found")).setActive(active);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }
}
