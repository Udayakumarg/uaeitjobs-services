package com.uaeitjobs.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {
    private static final Bandwidth AUTH_LIMIT = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!isAuthEndpoint(request.getRequestURI())) {
            return true;
        }

        String clientIp = clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, ignored -> Bucket4j.builder().addLimit(AUTH_LIMIT).build());
        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Rate limit exceeded. Max 5 requests per minute.\"}");
        log.warn("Rate limit exceeded for IP {} on {}", clientIp, request.getRequestURI());
        return false;
    }

    private boolean isAuthEndpoint(String uri) {
        return uri.equals("/api/v1/auth/login")
                || uri.equals("/api/v1/auth/register")
                || uri.equals("/api/v1/auth/refresh");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
