package com.evmonitor.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting for sensitive auth endpoints.
 *
 * Controlled by {@code app.security.rate-limiting.enabled}:
 *  - true  → limits are enforced (production default)
 *  - false → all requests pass through (development default, emergency kill-switch)
 *
 * Limits:
 *  - Login:    10 attempts / 5 min per IP
 *  - Register:  5 attempts / 10 min per IP
 */
@Service
@Slf4j
public class RateLimitService {

    @Value("${app.security.rate-limiting.enabled:false}")
    private boolean enabled;

    // 10 attempts, refilled every 5 minutes
    private static final Bandwidth LOGIN_LIMIT = Bandwidth.builder()
            .capacity(10)
            .refillIntervally(10, Duration.ofMinutes(5))
            .build();

    // 5 attempts, refilled every 10 minutes
    private static final Bandwidth REGISTER_LIMIT = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(10))
            .build();

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    /**
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeLogin(String clientIp) {
        if (!enabled) return true;
        boolean allowed = loginBuckets
                .computeIfAbsent(clientIp, ip -> Bucket.builder().addLimit(LOGIN_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for login from IP: {}", clientIp);
        }
        return allowed;
    }

    /**
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeRegister(String clientIp) {
        if (!enabled) return true;
        boolean allowed = registerBuckets
                .computeIfAbsent(clientIp, ip -> Bucket.builder().addLimit(REGISTER_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for register from IP: {}", clientIp);
        }
        return allowed;
    }
}
