package com.uaeitjobs.config;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot admin bootstrap.
 *
 * Set ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD in the environment
 * once. On the next startup the runner:
 *  - creates an admin user if one with that email doesn't already exist
 *  - marks them verified so they can log in immediately
 *
 * On subsequent startups it logs "already exists" and does nothing. Remove
 * the env vars after first run so the credentials are not stored on disk.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
    private String email;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(String... args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return; // nothing to do
        }
        if (userRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
            log.info("Admin bootstrap: user '{}' already exists — skipping.", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email.trim().toLowerCase());
        admin.setDisplayName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setUserType(UserType.admin);
        admin.setVerified(true);
        admin.setCountry("United Arab Emirates");
        userRepository.save(admin);
        log.info("Admin bootstrap: created admin user '{}'.", email);
    }
}
