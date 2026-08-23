package com.uaeitjobs.config;

import com.uaeitjobs.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Slf4j
@Component
public class JwtTokenProvider {
    /** Well-known weak/placeholder secrets that must never reach production. */
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "secret", "changeme", "changeme!", "mysecret", "jwt-secret",
            "your-secret-key", "your_jwt_secret_key",
            "change-this-dev-secret-change-this-dev-secret-change-this-dev-secret"
    );
    private static final int MIN_SECRET_BYTES = 64;

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        validateSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must not be blank");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret is too short — minimum " + MIN_SECRET_BYTES + " bytes required for HS256");
        }
        if (KNOWN_WEAK_SECRETS.contains(secret.toLowerCase())) {
            throw new IllegalStateException(
                    "app.jwt.secret looks like a placeholder value — set a strong random secret before starting");
        }
        log.info("JWT secret validated ({} bytes)", secret.getBytes(StandardCharsets.UTF_8).length);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getUserType().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
