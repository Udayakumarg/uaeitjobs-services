package com.uaeitjobs.service;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.UnauthorizedException;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

    public User get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        String email = principal instanceof org.springframework.security.core.userdetails.User userDetails
                ? userDetails.getUsername()
                : principal.toString();
        return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}
