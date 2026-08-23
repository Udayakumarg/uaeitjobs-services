package com.uaeitjobs.util;

import com.uaeitjobs.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",                 // loopback
            "http://localhost/",                 // loopback by name
            "http://169.254.169.254/latest/",    // link-local — the classic cloud metadata SSRF target
            "http://10.0.0.5/",                  // RFC1918 private
            "http://192.168.1.1/",               // RFC1918 private
            "http://0.0.0.0/",                   // any-local
    })
    void rejectsForbiddenAddresses(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsAPublicIpLiteral() {
        // An IP literal doesn't require a DNS lookup, so this stays deterministic
        // and network-free while still exercising the "not forbidden" branch.
        assertThatCode(() -> SsrfGuard.validate("http://93.184.216.34/"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> SsrfGuard.validate("not-a-url"))
                .isInstanceOf(ValidationException.class);
    }
}
