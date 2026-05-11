package com.uaeitjobs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {
    private final String verificationBaseUrl;

    public EmailService(@Value("${app.email.verification-base-url}") String verificationBaseUrl) {
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public void sendVerification(String email, String token) {
        log.info("Verification email for {}: {}?token={}", email, verificationBaseUrl, token);
    }
}
