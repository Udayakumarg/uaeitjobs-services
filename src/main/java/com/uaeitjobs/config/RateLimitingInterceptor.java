package com.uaeitjobs.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Two-tier rate limiter:
 * <ul>
 *   <li><b>Auth endpoints</b> ({@code /auth/login}, {@code /auth/register},
 *       {@code /auth/refresh}): 5 req / min per IP.</li>
 *   <li><b>Public read endpoints</b> ({@code /jobs/**}, {@code /skills/**},
 *       {@code /stats}, {@code /locations}): 60 req / min per IP.</li>
 * </ul>
 *
 * <p>Primary backend: Redis <em>sliding-window</em> Lua script backed by a
 * sorted set (ZSET) of request timestamps.  Every call removes entries older
 * than one window, counts what remains, and rejects if the count equals the
 * limit — so bursts cannot be doubled by straddling a fixed-window boundary.
 * The script is atomic (Redis single-threaded) and shared across all
 * application instances.
 *
 * <p>If Redis is unavailable the interceptor falls back transparently to
 * per-process Bucket4j token-bucket buckets (greedy refill) so the app
 * continues to function at the cost of per-instance (not global) limiting.
 */
@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    // ── Caffeine fallback limits (bucket4j 7.x API) ──────────────────────────
    // Greedy refill = token-bucket: tokens replenish continuously rather than
    // all-at-once at window reset, which prevents the fixed-window boundary
    // burst even in the local fallback path.
    private static final Bandwidth AUTH_BANDWIDTH =
            Bandwidth.classic(5,  Refill.greedy(5,  Duration.ofMinutes(1)));
    private static final Bandwidth PUBLIC_BANDWIDTH =
            Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));

    /** Bounded, TTL-evicting cache: max 10 000 keys, entries expire 5 min after last write. */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    // ── Redis ─────────────────────────────────────────────────────────────────
    /**
     * Injected only when {@code spring-boot-starter-data-redis} is on the
     * classpath AND a Redis server is reachable.  {@code required = false} so
     * the app starts normally in environments without Redis.
     */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    /**
     * Sliding-window rate-limit Lua script backed by a Redis sorted set.
     * <pre>
     * KEYS[1] = ZSET key               (e.g. "rl:auth:1.2.3.4")
     * ARGV[1] = per-window limit        (e.g. "5")
     * ARGV[2] = window duration seconds (e.g. "60")
     * Returns 1 = request allowed, 0 = request rejected.
     * </pre>
     *
     * <p>Algorithm (atomic — Redis is single-threaded):
     * <ol>
     *   <li>Read current time from Redis TIME (seconds + microseconds).</li>
     *   <li>Evict all ZSET members whose score is older than {@code now − window}.</li>
     *   <li>Count remaining members (requests in the current window).</li>
     *   <li>If count &lt; limit: add the current timestamp as a new member,
     *       refresh the key TTL, and return 1 (allowed).</li>
     *   <li>Otherwise return 0 (rejected).</li>
     * </ol>
     *
     * <p>Microseconds from TIME are used as ZSET member values so concurrent
     * requests within the same millisecond each get a unique entry.
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local t        = redis.call('TIME')
            local now_us   = tonumber(t[1]) * 1000000 + tonumber(t[2])
            local now_ms   = math.floor(now_us / 1000)
            local window_ms = tonumber(ARGV[2]) * 1000
            local limit    = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now_ms - window_ms)
            local count = tonumber(redis.call('ZCARD', KEYS[1]))
            if count < limit then
                redis.call('ZADD', KEYS[1], now_ms, now_us)
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]) + 1)
                return 1
            else
                return 0
            end
            """, Long.class);

    // ── HandlerInterceptor ────────────────────────────────────────────────────
    //
    // WebConfig registers this interceptor only on auth + public-read paths, so
    // preHandle is never called for unmetered endpoints (actuator, swagger, etc.).
    // The only distinction needed here is auth (5 req/min) vs public (60 req/min).

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String  uri    = request.getRequestURI();
        boolean isAuth = isAuthEndpoint(uri);
        String  ip     = clientIp(request);
        int     limit  = isAuth ? 5 : 60;
        String  key    = (isAuth ? "rl:auth:" : "rl:pub:") + ip;

        if (isAllowed(key, limit)) return true;

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again shortly.\"}");
        log.warn("Rate limit exceeded — IP={} uri={}", ip, uri);
        return false;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private boolean isAllowed(String key, int limit) {
        if (redis != null) {
            try {
                Long result = redis.execute(
                        RATE_LIMIT_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(limit),
                        "60");
                return Long.valueOf(1L).equals(result);
            } catch (Exception ex) {
                // Redis unreachable — fall through to per-process Caffeine fallback.
                log.debug("Redis rate-limit unavailable, using Caffeine fallback: {}", ex.getMessage());
            }
        }
        Bandwidth bw     = limit <= 5 ? AUTH_BANDWIDTH : PUBLIC_BANDWIDTH;
        Bucket    bucket = buckets.get(key, k -> Bucket4j.builder().addLimit(bw).build());
        return bucket.tryConsume(1);
    }

    private static boolean isAuthEndpoint(String uri) {
        return uri.equals("/api/v1/auth/login")
                || uri.equals("/api/v1/auth/register")
                || uri.equals("/api/v1/auth/refresh");
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
