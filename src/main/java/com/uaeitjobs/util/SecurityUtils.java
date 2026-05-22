package com.uaeitjobs.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Stateless helper methods for Spring Security context queries.
 * <p>
 * Centralising these checks here avoids duplicating the
 * {@code AnonymousAuthenticationToken} instanceof test across services.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code true} when the current request is made by a fully
     * authenticated user — i.e. the security context holds a non-null,
     * non-anonymous authentication token.
     */
    public static boolean isAuthenticated() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
