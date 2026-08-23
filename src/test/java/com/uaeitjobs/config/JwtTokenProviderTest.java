package com.uaeitjobs.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider("", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsTheCommittedDevPlaceholderEvenWhenExplicitlySet() {
        // A missing JWT_SECRET now resolves to "" (application.yml has no fallback),
        // which is caught by the blank check above. This test guards the other
        // path: someone copies the old placeholder into their env explicitly.
        assertThatThrownBy(() -> new JwtTokenProvider(
                "change-this-dev-secret-change-this-dev-secret-change-this-dev-secret", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void rejectsSecretShorterThan64Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void acceptsAStrongRandomSecret() {
        String strong = "a".repeat(64);
        new JwtTokenProvider(strong, 15); // must not throw
    }
}
