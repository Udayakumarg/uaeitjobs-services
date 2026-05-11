package com.uaeitjobs.util;

import jakarta.servlet.http.HttpServletRequest;

public final class JwtUtil {
    private JwtUtil() {
    }

    public static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }
}
