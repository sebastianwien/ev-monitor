package com.evmonitor.application;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Oeffentliches Teilen einzelner Ladekurven.
 *
 * Opt-in pro Ladung: geteilt wird immer genau eine Ladung, nie ein Konto und nie
 * ein Zeitraum. Der Token entsteht erst beim Teilen und ist mit einem Aufruf
 * wieder weg; ein erneutes Teilen vergibt einen neuen, der alte Link bleibt tot.
 *
 * Die Kurve ist ein bezahltes Analytics-Feature. Wer sie selbst nicht sehen darf,
 * darf sie auch nicht teilen - der oeffentliche Link waere sonst ein Weg am
 * Bezahl-Gate vorbei.
 */
@Service
public class EvLogShareService {

    private static final Logger log = LoggerFactory.getLogger(EvLogShareService.class);

    /** Pfad der oeffentlichen Seite - muss zur Vue-Route passen. */
    static final String SHARE_PATH = "/ladekurve/";

    private static final String TOKEN_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_LENGTH = 12;

    private final EvLogRepository evLogRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final String baseUrl;
    private final ApplicationEventPublisher eventPublisher;

    public EvLogShareService(EvLogRepository evLogRepository,
                             ObjectMapper objectMapper,
                             ApplicationEventPublisher eventPublisher,
                             @Value("${app.base-url}") String baseUrl) {
        this.evLogRepository = evLogRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Gibt die Ladung oeffentlich frei und liefert die URL. Mehrfaches Aufrufen
     * liefert denselben Token - sonst blieben Links im Umlauf, die der Nutzer
     * spaeter nicht mehr widerrufen kann.
     *
     * @throws IllegalArgumentException Log unbekannt oder gehoert einem anderen Nutzer (-> 404)
     * @throws AccessDeniedException    Nutzer darf Ladekurven nicht sehen (-> 403)
     * @throws IllegalStateException    zu dieser Ladung existiert keine Kurve (-> 409)
     */
    @Transactional
    public ShareResponse createShare(UUID logId, User user) {
        requireOwnedCurve(logId, user);

        String existing = evLogRepository.findShareToken(logId).orElse(null);
        if (existing != null && !existing.isBlank()) {
            return toResponse(existing);
        }

        String token = generateToken();
        evLogRepository.setShareToken(logId, token, LocalDateTime.now());
        log.info("Ladekurve geteilt: log={} user={}", logId, user.getId());
        return toResponse(token);
    }

    /** Macht die oeffentliche URL ungueltig. Idempotent. */
    @Transactional
    public void revokeShare(UUID logId, User user) {
        // Bewusst ohne Entitlement-Pruefung: wer den Zugriff auf Ladekurven
        // verliert, muss seine alten Links trotzdem zurueckziehen koennen.
        requireOwnership(logId, user);
        String token = evLogRepository.findShareToken(logId).orElse(null);
        evLogRepository.clearShareToken(logId);
        if (token != null && !token.isBlank()) {
            // Nimmt das gecachte Vorschaubild mit - sonst laege es noch aus,
            // waehrend die Seite selbst schon tot ist.
            eventPublisher.publishEvent(new ShareRevokedEvent(token));
        }
        log.info("Ladekurve nicht mehr geteilt: log={} user={}", logId, user.getId());
    }

    /** Aktueller Token der Ladung, leer wenn sie nicht geteilt ist. */
    @Transactional(readOnly = true)
    public Optional<ShareResponse> findShare(UUID logId, User user) {
        requireOwnership(logId, user);
        return evLogRepository.findShareToken(logId)
                .filter(t -> !t.isBlank())
                .map(this::toResponse);
    }

    /**
     * Oeffentlicher Lookup - ohne Authentifizierung, ohne Bezug auf einen Nutzer.
     * Leeres Optional fuer unbekannte, widerrufene und kaputte Kurven gleichermassen:
     * der Aufrufer soll die Faelle nicht unterscheiden koennen.
     */
    @Transactional(readOnly = true)
    public Optional<PublicCurveResponse> getPublicCurve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        return evLogRepository.findPublicCurveByShareToken(token).flatMap(row -> {
            List<PowerCurveResponse.Point> points = parsePoints(row.powerCurvePointsJson());
            if (points.isEmpty()) return Optional.empty();

            return Optional.of(new PublicCurveResponse(
                    points,
                    row.carModel() != null
                            ? row.carModel().getBrand().getDisplayString() + " " + row.carModel().getDisplayName()
                            : null,
                    row.kwhAtVehicle() != null ? row.kwhAtVehicle() : row.kwhCharged(),
                    row.chargeDurationMinutes(),
                    row.socBeforeChargePercent(),
                    row.socAfterChargePercent(),
                    peakKw(row.maxChargingPowerKw(), points),
                    row.cpoName(),
                    row.chargingType(),
                    row.loggedAt() != null ? row.loggedAt().toLocalDate() : null));
        });
    }

    // ── intern ───────────────────────────────────────────────────────────────

    /** Ownership zuerst, damit ein Fremder auch bei fehlendem Entitlement nur 404 sieht. */
    private EvLogRepository.PowerCurveLookup requireOwnership(UUID logId, User user) {
        EvLogRepository.PowerCurveLookup lookup = evLogRepository.findOwnerIdAndPowerCurveJson(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + logId));
        if (!lookup.ownerUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }
        return lookup;
    }

    private void requireOwnedCurve(UUID logId, User user) {
        EvLogRepository.PowerCurveLookup lookup = requireOwnership(logId, user);
        if (!user.canViewLiveAnalytics()) {
            throw new AccessDeniedException("Ladekurven sind ein AutoSync-Live-Feature.");
        }
        String json = lookup.powerCurvePointsJson();
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Zu dieser Ladung wurde keine Kurve aufgezeichnet.");
        }
    }

    private ShareResponse toResponse(String token) {
        return new ShareResponse(token, baseUrl + SHARE_PATH + token);
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_ALPHABET.charAt(random.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private List<PowerCurveResponse.Point> parsePoints(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, PowerCurveResponse.Point.class));
        } catch (Exception e) {
            log.warn("Geteilte Ladekurve nicht lesbar: {}", e.getMessage());
            return List.of();
        }
    }

    /** Log-Wert bevorzugt - er stammt aus den rohen Events und ist feiner als das Kurven-Maximum. */
    private BigDecimal peakKw(BigDecimal fromLog, List<PowerCurveResponse.Point> points) {
        if (fromLog != null) return fromLog;
        return points.stream()
                .map(p -> BigDecimal.valueOf(p.kw()))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
