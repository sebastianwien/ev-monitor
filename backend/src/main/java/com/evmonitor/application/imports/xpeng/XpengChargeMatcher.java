package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.xpeng.DetectedChargingSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Verheiratet detektierte XPeng-Ladevorgänge mit bereits existierenden ev_log-Einträgen
 * (z.B. manuelle Wallbox-Einträge mit Brutto-kWh + Preis), bevor unmatched Sessions als
 * neue Logs angelegt werden.
 *
 * Matching:
 * - Odometer ±1 km (falls Session Odometer hat)
 * - Zeitfenster ±10 min um Session-Ende (immer geprüft)
 *
 * Enrichment-Regeln:
 * - kwhAtVehicle leer → mit Session-Wert füllen (XPeng misst fahrzeugseitig = netto)
 * - kwhAtVehicle bereits gesetzt → unangetastet lassen (User-Edit / früherer Import gewinnt)
 * - maxChargingPowerKw / chargingType: nur befüllen wenn null
 * - kwhCharged (Brutto), costEur, cpoName, chargingProviderId bleiben immer unangetastet
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XpengChargeMatcher {

    private static final ObjectMapper EXTRAS_MAPPER = new ObjectMapper();

    private final EvLogRepository evLogRepository;

    public record MatchResult(int enriched, List<DetectedChargingSession> unmatched) {}

    @Transactional
    public MatchResult matchAndEnrich(UUID carId, List<DetectedChargingSession> sessions) {
        if (sessions.isEmpty()) return new MatchResult(0, List.of());

        List<DetectedChargingSession> unmatched = new ArrayList<>();
        int enriched = 0;

        for (DetectedChargingSession s : sessions) {
            // Detector liefert MIN_SESSION_KWH=0.05; null waere bug-Indikator, nicht enrichen.
            if (s.kwhCharged() == null) {
                unmatched.add(s);
                continue;
            }
            Integer odo = s.odometerKm() == null ? null : s.odometerKm().intValue();
            Optional<EvLog> candidate = evLogRepository.findChargeMatchCandidate(carId, odo, s.endedAt());

            if (candidate.isEmpty()) {
                unmatched.add(s);
                continue;
            }
            EvLog existing = candidate.get();
            if (existing.getKwhAtVehicle() != null) {
                log.debug("XpengChargeMatcher: log {} hat bereits kwhAtVehicle - skip", existing.getId());
                continue;
            }

            EvLog patched = existing.toBuilder()
                    .kwhAtVehicle(s.kwhCharged())
                    .maxChargingPowerKw(existing.getMaxChargingPowerKw() != null
                            ? existing.getMaxChargingPowerKw()
                            : s.maxPowerKw())
                    .chargingType(existing.getChargingType() != null && existing.getChargingType() != ChargingType.UNKNOWN
                            ? existing.getChargingType()
                            : parseChargingType(s.chargingType()))
                    .updatedAt(LocalDateTime.now())
                    .build();
            evLogRepository.save(patched);

            String extrasJson = serializeExtras(s.telemetryExtras());
            if (extrasJson != null) {
                try {
                    evLogRepository.updateTelemetryExtrasById(existing.getId(), extrasJson);
                } catch (Exception e) {
                    log.warn("XpengChargeMatcher: telemetry_extras update failed for log {}",
                            existing.getId(), e);
                }
            }
            enriched++;
        }

        log.info("XpengChargeMatcher: car={} enriched={} unmatched={}",
                carId, enriched, unmatched.size());
        return new MatchResult(enriched, unmatched);
    }

    /**
     * "UNKNOWN" wird zu null gemappt, damit der EvLog-Konstruktor via inferChargingType()
     * aus maxPowerKw/kWh/Duration ableiten kann statt UNKNOWN zu persistieren.
     */
    private static ChargingType parseChargingType(String raw) {
        if (raw == null || "UNKNOWN".equalsIgnoreCase(raw)) return null;
        try {
            return ChargingType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String serializeExtras(Map<String, Object> extras) {
        if (extras == null || extras.isEmpty()) return null;
        try {
            return EXTRAS_MAPPER.writeValueAsString(extras);
        } catch (Exception e) {
            log.warn("XpengChargeMatcher: extras JSON serialize failed", e);
            return null;
        }
    }
}
