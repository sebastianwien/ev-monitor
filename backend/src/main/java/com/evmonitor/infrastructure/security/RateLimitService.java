package com.evmonitor.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * IP-based rate limiting for sensitive auth endpoints.
 *
 * Controlled by {@code app.security.rate-limiting.enabled}:
 *  - true  → limits are enforced (production default)
 *  - false → all requests pass through (development default, emergency kill-switch)
 *
 * Limits:
 *  - Login:          10 attempts / 5 min per IP
 *  - Register:        5 attempts / 10 min per IP
 *  - Forgot Password: 3 attempts / 10 min per IP
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

    // 3 attempts, refilled every 10 minutes
    private static final Bandwidth FORGOT_PASSWORD_LIMIT = Bandwidth.builder()
            .capacity(3)
            .refillIntervally(3, Duration.ofMinutes(10))
            .build();

    // 5 demo logins per 10 minutes per IP
    private static final Bandwidth DEMO_LOGIN_LIMIT = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(10))
            .build();

    // 60 API upload requests per hour per API Key ID
    private static final Bandwidth API_UPLOAD_LIMIT = Bandwidth.builder()
            .capacity(60)
            .refillIntervally(60, Duration.ofHours(1))
            .build();

    // Caffeine caches mit TTL + Größen-Limit — verhindert unbegrenztes Wachstum der Buckets.
    // expireAfterAccess: Bucket wird nach Inaktivität entfernt. maximumSize: Hard Cap gegen DoS.
    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(5_000).build();
    private final Cache<String, Bucket> registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(5_000).build();
    private final Cache<String, Bucket> forgotPasswordBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(5_000).build();
    private final Cache<String, Bucket> demoLoginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(5_000).build();
    private final Cache<String, Bucket> apiUploadBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();

    /**
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeLogin(String clientIp) {
        if (!enabled) return true;
        boolean allowed = loginBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(LOGIN_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for login from IP: {}", clientIp);
        }
        return allowed;
    }

    public boolean tryConsumeForgotPassword(String clientIp) {
        if (!enabled) return true;
        boolean allowed = forgotPasswordBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(FORGOT_PASSWORD_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for forgot-password from IP: {}", clientIp);
        }
        return allowed;
    }

    /**
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeRegister(String clientIp) {
        if (!enabled) return true;
        boolean allowed = registerBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(REGISTER_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for register from IP: {}", clientIp);
        }
        return allowed;
    }

    /**
     * Rate limiting per API Key ID: 60 requests/hour.
     * @param keyId the API key UUID as string (used as bucket key)
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeApiUpload(String keyId) {
        if (!enabled) return true;
        boolean allowed = apiUploadBuckets
                .get(keyId, k -> Bucket.builder().addLimit(API_UPLOAD_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for API upload, key: {}", keyId);
        }
        return allowed;
    }

    public boolean tryConsumeDemoLogin(String clientIp) {
        if (!enabled) return true;
        boolean allowed = demoLoginBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(DEMO_LOGIN_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for demo-login from IP: {}", clientIp);
        }
        return allowed;
    }
}
