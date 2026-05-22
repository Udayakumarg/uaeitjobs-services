package com.uaeitjobs.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed Spring Cache configuration.
 *
 * <p>Currently defined caches:
 * <ul>
 *   <li><b>users</b> — keyed by lower-cased email; TTL 2 min, max 2 000 entries.
 *       Prevents a DB round-trip on every authenticated request handled by
 *       {@link com.uaeitjobs.service.CurrentUserService}.  User records change
 *       rarely (email verification, profile edits) so a short TTL is safe.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("users");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(2, TimeUnit.MINUTES)
        );
        return manager;
    }
}
