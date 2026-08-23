package com.uaeitjobs.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final RateLimitingInterceptor rateLimitingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only meter the paths that are actually rate-limited.
        // This avoids running preHandle on every static-asset and actuator call.
        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/contact",
                        "/api/v1/jobs/**",
                        "/api/v1/skills/**",
                        "/api/v1/stats",
                        "/api/v1/locations",
                        // Makes a synchronous outbound LLM call — was previously unmetered,
                        // so a looped request could exhaust the DB connection pool.
                        "/api/v1/job-seeker/applications/draft"
                );
    }
}
