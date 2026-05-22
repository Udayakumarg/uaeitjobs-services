package com.uaeitjobs.service;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.UnauthorizedException;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Returns the {@link User} for the current request's JWT principal.
     * Delegates to {@link #getByEmail(String)} which is Caffeine-cached for
     * 2 minutes (see {@link com.uaeitjobs.config.CacheConfig}) to avoid a DB
     * round-trip on every authenticated endpoint call.
     */
    public User get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new UnauthorizedException("Authentication required");
        }
        String email = auth.getPrincipal() instanceof UserDetails ud
                ? ud.getUsername()
                : auth.getPrincipal().toString();
        return getByEmail(email);
    }

    /** Cached by lower-cased email. Call {@link #evict(String)} after user mutations. */
    @Cacheable(value = "users", key = "#email.toLowerCase()")
    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    /**
     * Evicts the cached entry for {@code email}.  Call after operations that
     * mutate user state (email verification, role changes, password reset).
     */
    @CacheEvict(value = "users", key = "#email.toLowerCase()")
    public void evict(String email) {
        // Spring AOP handles the eviction — no body needed.
    }
}
