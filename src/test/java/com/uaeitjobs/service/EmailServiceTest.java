package com.uaeitjobs.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailServiceTest {
    @Test
    void missingSendGridKeyIsAllowedOutsideProduction() {
        EmailService emailService = new EmailService(
                "",
                "noreply@uaeitjobs.test",
                "UAEITJOBS Test",
                "http://localhost:3000",
                new MockEnvironment().withProperty("spring.profiles.active", "test")
        );

        assertThatCode(() -> emailService.sendVerificationEmail("user@example.com", "token"))
                .doesNotThrowAnyException();
    }

    @Test
    void missingSendGridKeyFailsInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        EmailService emailService = new EmailService(
                "",
                "noreply@uaeitjobs.com",
                "UAEITJOBS",
                "https://uaeitjobs.com",
                environment
        );

        assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENDGRID_API_KEY");
    }

    @Test
    void missingSendGridKeyFailsWhenEnvironmentIsNullButSystemProfileIsProduction() {
        String previous = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "prod");
        try {
            EmailService emailService = new EmailService(
                    "",
                    "noreply@uaeitjobs.com",
                    "UAEITJOBS",
                    "https://uaeitjobs.com",
                    null
            );

            assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "token"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SENDGRID_API_KEY");
        } finally {
            if (previous == null) {
                System.clearProperty("spring.profiles.active");
            } else {
                System.setProperty("spring.profiles.active", previous);
            }
        }
    }
}
