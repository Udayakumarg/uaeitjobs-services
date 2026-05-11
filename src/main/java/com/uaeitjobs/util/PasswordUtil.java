package com.uaeitjobs.util;

import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordUtil {
    private PasswordUtil() {
    }

    public static String hash(PasswordEncoder encoder, String rawPassword) {
        return encoder.encode(rawPassword);
    }
}
