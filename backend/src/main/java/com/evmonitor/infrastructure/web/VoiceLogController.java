package com.evmonitor.infrastructure.web;

import com.evmonitor.application.NearbyStation;
import com.evmonitor.application.voice.DraftFields;
import com.evmonitor.application.voice.PlaceDraft;
import com.evmonitor.application.voice.VoiceDraftCommand;
import com.evmonitor.application.voice.VoiceDraftResult;
import com.evmonitor.application.voice.VoiceLogService;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceQuota;
import com.evmonitor.application.voice.VoiceQuotaExceededException;
import com.evmonitor.application.voice.VoiceQuotaService;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sprachlog: Aufnahme rein, Entwurf fuer die Pruefansicht raus. Gespeichert wird hier nichts,
 * das Log entsteht erst, wenn der Nutzer den Entwurf im Wizard abschickt.
 *
 * <p>Notschalter: mit {@code VOICE_LOG_ADMIN_ONLY=true} existieren die Endpoints nur fuer Admins,
 * alle anderen bekommen 404 und das Frontend blendet das Mikrofon aus.
 */
@RestController
@RequestMapping("/api/logs")
public class VoiceLogController {

    /** 60 s Opus oder AAC liegen weit darunter. */
    static final long MAX_AUDIO_BYTES = 2L * 1024 * 1024;
    static final Set<String> ALLOWED_TYPES = Set.of("audio/mp4", "audio/webm", "audio/ogg", "audio/wav");

    private final VoiceLogService voiceLogService;
    private final VoiceQuotaService quotaService;
    private final RateLimitService rateLimitService;
    private final boolean adminOnly;

    public VoiceLogController(VoiceLogService voiceLogService, VoiceQuotaService quotaService, RateLimitService rateLimitService,
                              @Value("${voice-log.admin-only:false}") boolean adminOnly) {
        this.voiceLogService = voiceLogService;
        this.quotaService = quotaService;
        this.rateLimitService = rateLimitService;
        this.adminOnly = adminOnly;
    }

    /** Stand vor der Aufnahme, damit das Mikrofon den Hinweis oder das aufgebrauchte Kontingent zeigen kann. */
    @GetMapping("/voice-quota")
    public ResponseEntity<?> voiceQuota(@RequestParam String timeZone, Authentication authentication) {
        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();
        if (adminOnly && !"ADMIN".equals(user.getRole())) return ResponseEntity.notFound().build();
        ZoneId zone = zone(timeZone);
        if (zone == null) return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        VoiceQuota q = quotaService.quota(user.getId(), zone);
        return ResponseEntity.ok(new QuotaResponse(planName(q), q.limit(), q.remaining(), q.exhausted(), q.resetsOn()));
    }

    @PostMapping(value = "/voice-draft", consumes = "multipart/form-data")
    public ResponseEntity<?> voiceDraft(@RequestParam("audio") MultipartFile audio,
                                        @RequestParam UUID carId,
                                        @RequestParam(required = false) Double lat,
                                        @RequestParam(required = false) Double lon,
                                        @RequestParam(required = false) String timeZone,
                                        Authentication authentication) throws IOException {
        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();
        if (adminOnly && !"ADMIN".equals(user.getRole())) return ResponseEntity.notFound().build();
        UUID userId = user.getId();
        if (audio.isEmpty()) return error(HttpStatus.BAD_REQUEST, "VOICE_EMPTY");
        if (audio.getSize() > MAX_AUDIO_BYTES) return error(HttpStatus.PAYLOAD_TOO_LARGE, "VOICE_TOO_LARGE");
        String mimeType = baseType(audio.getContentType());
        if (!ALLOWED_TYPES.contains(mimeType)) return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VOICE_UNSUPPORTED_TYPE");
        ZoneId zone = zone(timeZone);
        if (zone == null || !validPosition(lat, lon)) return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        if (!rateLimitService.tryConsumeVoiceDraft(userId.toString())) return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");

        VoiceDraftResult result = voiceLogService.draft(
                new VoiceDraftCommand(userId, carId, audio.getBytes(), mimeType, lat, lon, zone));
        return ResponseEntity.ok(VoiceDraftResponse.from(result));
    }

    @ExceptionHandler(VoiceQuotaExceededException.class)
    ResponseEntity<Map<String, Object>> quotaExceeded(VoiceQuotaExceededException e) {
        VoiceQuota q = e.quota();
        // Admins haben keinen sichtbaren Deckel, nur das stille Fair-Use-Limit
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(q.limit() == null
                ? Map.of("code", "VOICE_LIMIT_REACHED", "plan", planName(q), "resetsOn", q.resetsOn())
                : Map.of("code", "VOICE_LIMIT_REACHED", "plan", planName(q), "limit", q.limit(), "resetsOn", q.resetsOn()));
    }

    @ExceptionHandler(VoiceProviderException.class)
    ResponseEntity<Map<String, Object>> providerFailed(VoiceProviderException e) {
        return switch (e.reason()) {
            case EMPTY_TRANSCRIPT -> error(HttpStatus.UNPROCESSABLE_ENTITY, "VOICE_NOT_UNDERSTOOD");
            case RATE_LIMITED, UNAVAILABLE, NOT_CONFIGURED -> error(HttpStatus.SERVICE_UNAVAILABLE, "VOICE_UNAVAILABLE");
            case REJECTED, INVALID_RESPONSE -> error(HttpStatus.BAD_GATEWAY, "VOICE_FAILED");
        };
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code));
    }

    /** "audio/webm;codecs=opus" -> "audio/webm" */
    static String baseType(String contentType) {
        if (contentType == null) return "";
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) return null;
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean validPosition(Double lat, Double lon) {
        if (lat == null && lon == null) return true;
        if (lat == null || lon == null) return false;
        return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }

    public record VoiceDraftResponse(String transcript, DraftFields fields, PlaceResponse place,
                                     UUID chargingProviderId, UsageResponse usage) {
        static VoiceDraftResponse from(VoiceDraftResult r) {
            return new VoiceDraftResponse(r.transcript(), r.fields(), PlaceResponse.from(r.place()), r.chargingProviderId(),
                    new UsageResponse(planName(r.quota()), r.quota().limit(), r.quota().remaining(), r.quota().resetsOn()));
        }
    }

    /** Form der {@code PlaceChoice} im Wizard; station wie die Umkreissuche, site wie die zuletzt genutzten Standorte. */
    public record PlaceResponse(String kind, NearbyStation station,
                                ChargingSiteController.RecentChargingSiteResponse site, String cpoName) {
        static PlaceResponse from(PlaceDraft p) {
            if (p == null) return null;
            return new PlaceResponse(p.kind(), p.station(),
                    p.site() == null ? null : ChargingSiteController.RecentChargingSiteResponse.from(p.site()), p.cpoName());
        }
    }

    /** @param limit null bei bezahlten Tiers, dort gibt es keinen sichtbaren Zaehler */
    public record UsageResponse(String plan, Integer limit, Integer remaining, LocalDate resetsOn) {}

    public record QuotaResponse(String plan, Integer limit, Integer remaining, boolean exhausted, LocalDate resetsOn) {}

    /** free, paid oder admin */
    private static String planName(VoiceQuota q) {
        return q.plan().name().toLowerCase(Locale.ROOT);
    }
}
