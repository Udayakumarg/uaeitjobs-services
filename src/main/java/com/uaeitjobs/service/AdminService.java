package com.uaeitjobs.service;

import com.uaeitjobs.dto.AdminDTO;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

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

    /** Create any user type (including admin) — admin-created accounts are pre-verified. */
    @Transactional
    public User createUser(String email, String password, UserType userType) {
        if (email == null || email.isBlank()) throw new ValidationException("Email is required");
        if (password == null || password.length() < 8) throw new ValidationException("Password must be at least 8 characters");
        if (userRepository.existsByEmailIgnoreCase(email)) throw new ValidationException("Email is already registered");
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUserType(userType);
        user.setVerified(true); // admin-created accounts skip email verification
        return userRepository.save(user);
    }
}
