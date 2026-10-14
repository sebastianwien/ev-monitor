package com.evmonitor.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    // 60 CPO-Umkreisabfragen pro Stunde und IP, gezaehlt nur ohne Cache-Treffer (die erreichen das
    // Register nicht). Reicht fuer das Eintragen von Ladungen,
    // verhindert aber, den Endpunkt als kostenlosen Proxy auf das Ladesaeulenregister zu nutzen.
    private static final Bandwidth CPO_LOOKUP_LIMIT = Bandwidth.builder()
            .capacity(60)
            .refillIntervally(60, Duration.ofHours(1))
            .build();

    // 600 Textsuchen im Ladesaeulenregister pro Stunde und Nutzer: Autocomplete tippt viele Anfragen,
    // der Umkreis-Topf mit 60 waere nach einem Log leer. Eigener Topf, damit beides unabhaengig bleibt.
    private static final Bandwidth STATION_SEARCH_LIMIT = Bandwidth.builder()
            .capacity(600)
            .refillIntervally(600, Duration.ofHours(1))
            .build();

    // 120 demo read requests per minute per IP - generous for human browsing, blocks scraping.
    private static final Bandwidth DEMO_REQUEST_LIMIT = Bandwidth.builder()
            .capacity(120)
            .refillIntervally(120, Duration.ofMinutes(1))
            .build();

    // 10 VW-ID-Logins pro Stunde und Nutzer - der EUDA-Connect darf kein Brute-Force-Proxy gegen VW sein
    private static final Bandwidth EUDA_LOGIN_LIMIT = Bandwidth.builder()
            .capacity(10)
            .refillIntervally(10, Duration.ofHours(1))
            .build();

    // 20 Sprachaufnahmen pro Stunde und Nutzer. Fehlschlaege zaehlen nicht gegen den Monatsdeckel,
    // kosten bei Mistral aber trotzdem - ohne diesen Topf liessen sie sich beliebig wiederholen.
    private static final Bandwidth VOICE_DRAFT_LIMIT = Bandwidth.builder()
            .capacity(20)
            .refillIntervally(20, Duration.ofHours(1))
            .build();

    // Login-Code per Mail: 5 Anforderungen je 15 Minuten und IP, 3 je 15 Minuten und Adresse.
    // Der Adress-Topf verhindert, dass jemand fremde Postfächer mit Codes flutet.
    private static final Bandwidth CODE_REQUEST_IP_LIMIT = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(15))
            .build();
    private static final Bandwidth CODE_REQUEST_EMAIL_LIMIT = Bandwidth.builder()
            .capacity(3)
            .refillIntervally(3, Duration.ofMinutes(15))
            .build();
    // Range band before the gate on the model pages, per IP: every chip click costs one call, 30 an hour
    // cover a visitor playing through all four chapters twice. Beyond that the page shows the model band
    // and points to the gate. Raise to 60 if a mobile carrier NAT shows up in the 429 log after a forum post.
    static final int PREVIEW_PER_HOUR = 30;
    private static final Bandwidth PREVIEW_LIMIT = Bandwidth.builder()
            .capacity(PREVIEW_PER_HOUR)
            .refillIntervally(PREVIEW_PER_HOUR, Duration.ofHours(1))
            .build();
    // Model ranking for a profile, per IP: one call runs every listed model through the calculator (all
    // from cache). The list asks once per settled answer, 60 an hour cover a visitor trying many profiles.
    static final int RANKING_PER_HOUR = 60;
    private static final Bandwidth RANKING_LIMIT = Bandwidth.builder()
            .capacity(RANKING_PER_HOUR)
            .refillIntervally(RANKING_PER_HOUR, Duration.ofHours(1))
            .build();
    // Result mails of the range calculator: the gate promises one, a few more cover retries
    private static final Bandwidth RESULT_MAIL_LIMIT = Bandwidth.builder()
            .capacity(3)
            .refillIntervally(3, Duration.ofHours(24))
            .build();

    // Caffeine caches mit TTL + Größen-Limit - verhindert unbegrenztes Wachstum der Buckets.
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
    private final Cache<String, Bucket> demoRequestBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES).maximumSize(10_000).build();
    private final Cache<String, Bucket> cpoLookupBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();
    private final Cache<String, Bucket> stationSearchBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();
    private final Cache<String, Bucket> eudaLoginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();
    private final Cache<String, Bucket> voiceDraftBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();

    private final Cache<String, Bucket> codeRequestIpBuckets = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES).maximumSize(10_000).build();
    private final Cache<String, Bucket> codeRequestEmailBuckets = Caffeine.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES).maximumSize(10_000).build();
    private final Cache<String, Bucket> resultMailBuckets = Caffeine.newBuilder()
            .expireAfterAccess(25, TimeUnit.HOURS).maximumSize(10_000).build();
    private final Cache<String, Bucket> previewBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();
    private final Cache<String, Bucket> rankingBuckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS).maximumSize(10_000).build();
    /** Refused previews since start, in the log line so a NAT behind one IP stands out. */
    private final AtomicLong previewRejections = new AtomicLong();

    /** Model rankings for a profile per IP; exceeding it answers 429. */
    public boolean tryConsumeRanking(String clientIp) {
        if (!enabled) return true;
        boolean allowed = rankingBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(RANKING_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for model ranking from IP: {}", clientIp);
        }
        return allowed;
    }

    /** Range band previews per IP; exceeding it answers 429. */
    public boolean tryConsumePreview(String clientIp) {
        if (!enabled) return true;
        boolean allowed = previewBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(PREVIEW_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for range preview from IP: {} (refused so far: {})",
                    clientIp, previewRejections.incrementAndGet());
        }
        return allowed;
    }

    /** Result mails of the range calculator per user, 3 a day; beyond that the mail is skipped silently. */
    public boolean tryConsumeResultMail(UUID userId) {
        if (!enabled) return true;
        return resultMailBuckets
                .get(userId.toString(), k -> Bucket.builder().addLimit(RESULT_MAIL_LIMIT).build())
                .tryConsume(1);
    }

    /** Code requests per IP; exceeding it answers 429. */
    public boolean tryConsumeCodeRequestIp(String clientIp) {
        if (!enabled) return true;
        boolean allowed = codeRequestIpBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(CODE_REQUEST_IP_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for login code request from IP: {}", clientIp);
        }
        return allowed;
    }

    /** Code mails per address (normalized by the caller); exceeding it sends nothing, the last code stays valid. */
    public boolean tryConsumeCodeMail(String email) {
        if (!enabled) return true;
        return codeRequestEmailBuckets
                .get(email, e -> Bucket.builder().addLimit(CODE_REQUEST_EMAIL_LIMIT).build())
                .tryConsume(1);
    }

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

    /**
     * Throttles read traffic of an active demo session per IP. Demo tokens are public, so this
     * caps scraping/DoS of the (real) account's data behind them.
     *
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeDemoRequest(String clientIp) {
        if (!enabled) return true;
        boolean allowed = demoRequestBuckets
                .get(clientIp, ip -> Bucket.builder().addLimit(DEMO_REQUEST_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for demo request from IP: {}", clientIp);
        }
        return allowed;
    }

    /**
     * Drosselt die Abfragen beim Ladesaeulenregister je Schluessel (Nutzer).
     *
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeCpoLookup(String key) {
        if (!enabled) return true;
        boolean allowed = cpoLookupBuckets
                .get(key, k -> Bucket.builder().addLimit(CPO_LOOKUP_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for CPO lookup: {}", key);
        }
        return allowed;
    }

    /** Drosselt die Textsuche im Ladesaeulenregister je Nutzer. */
    public boolean tryConsumeStationSearch(String key) {
        if (!enabled) return true;
        boolean allowed = stationSearchBuckets
                .get(key, k -> Bucket.builder().addLimit(STATION_SEARCH_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for station search: {}", key);
        }
        return allowed;
    }

    /**
     * VW-ID-Login fuer den EU-Data-Act-AutoSync, Schluessel ist die User-ID.
     *
     * @return true if the request may proceed, false if rate limit exceeded
     */
    public boolean tryConsumeVwEudaLogin(String userId) {
        if (!enabled) return true;
        boolean allowed = eudaLoginBuckets
                .get(userId, k -> Bucket.builder().addLimit(EUDA_LOGIN_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for EUDA login: userId={}", userId);
        }
        return allowed;
    }

    /** Sprachlog je Nutzer, Schluessel ist die User-ID. */
    public boolean tryConsumeVoiceDraft(String userId) {
        if (!enabled) return true;
        boolean allowed = voiceDraftBuckets
                .get(userId, k -> Bucket.builder().addLimit(VOICE_DRAFT_LIMIT).build())
                .tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for voice draft: userId={}", userId);
        }
        return allowed;
    }
}
