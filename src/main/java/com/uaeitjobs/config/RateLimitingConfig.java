package com.uaeitjobs.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitingConfig implements WebMvcConfigurer {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthRateLimitInterceptor()).addPathPatterns("/api/v1/auth/**");
    }

    private class AuthRateLimitInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String key = request.getRemoteAddr();
            Bucket bucket = buckets.compute(key, (k, existing) -> existing == null || existing.expiresAt < Instant.now().getEpochSecond()
                    ? new Bucket(Instant.now().getEpochSecond() + 60)
                    : existing);
            if (bucket.count.incrementAndGet() > 5) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many auth requests");
                return false;
            }
            return true;
        }
    }

    private static class Bucket {
        private final long expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Bucket(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
