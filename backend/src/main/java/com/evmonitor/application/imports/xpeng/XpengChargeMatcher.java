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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verheiratet detektierte XPeng-Ladevorgaenge mit bereits existierenden ev_log-Eintraegen
 * (typischerweise Brutto-Wallbox-Logs ueber die Public-API).
 *
 * Match-Strategie:
 *   1. Sessions ohne Odometer werden direkt unmatched -> Service legt neue XPENG_IMPORT-Logs an.
 *   2. Sessions mit Odometer werden nach Odometer-Wert gruppiert (n XPeng-Sub-Sessions am
 *      gleichen Kilometerstand gehoeren physikalisch zum selben Ladevorgang).
 *   3. Pro Odo-Gruppe: Repository liefert alle ev_logs mit Odo ±1 km im Zeitbereich
 *      [min(startedAt) - 1d, max(endedAt) + 1d]. Das 1-Tages-Fenster faengt Nachtladungen
 *      und Timezone-Drift ab.
 *   4. Tie-Breaker bei mehreren Kandidaten: zeitlich naechster zur fruehesten Session-Start-Zeit.
 *   5. Hat der gewaehlte Kandidat schon kwh_at_vehicle -> skip (kein doppelter Eintrag,
 *      auch kein unmatched, weil die Daten konzeptionell schon drin sind).
 *   6. Sonst: Summe aller Session-kWh in kwh_at_vehicle, max_power und charging_type
 *      werden nur befuellt wenn leer. Brutto-kWh und Preis bleiben unangetastet.
 *   7. Restliche Sessions (kein passender ev_log gefunden) -> unmatched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XpengChargeMatcher {

    /** Wie weit logged_at von der Session-Range abweichen darf - faengt Nachtladungen + TZ-Drift ab. */
    private static final Duration TIME_SLACK = Duration.ofDays(1);

    private static final ObjectMapper EXTRAS_MAPPER = new ObjectMapper();

    private final EvLogRepository evLogRepository;

    public record MatchResult(int enriched, List<DetectedChargingSession> unmatched) {}

    @Transactional
    public MatchResult matchAndEnrich(UUID carId, List<DetectedChargingSession> sessions) {
        if (sessions.isEmpty()) return new MatchResult(0, List.of());

        List<DetectedChargingSession> unmatched = new ArrayList<>();
        Map<Integer, List<DetectedChargingSession>> byOdometer = new HashMap<>();

        // Pass 1: alles ohne Odometer faellt sofort durch
        for (DetectedChargingSession s : sessions) {
            if (s.kwhCharged() == null) { // Detector-Bug-Indikator, nicht enrichen
                unmatched.add(s);
                continue;
            }
            if (s.odometerKm() == null) {
                unmatched.add(s);
                continue;
            }
            byOdometer
                    .computeIfAbsent(s.odometerKm().intValue(), k -> new ArrayList<>())
                    .add(s);
        }

        int enriched = 0;
        for (Map.Entry<Integer, List<DetectedChargingSession>> entry : byOdometer.entrySet()) {
            int odo = entry.getKey();
            List<DetectedChargingSession> group = entry.getValue();
            switch (tryEnrichGroup(carId, odo, group)) {
                case ENRICHED -> enriched++;
                case SKIPPED -> { /* schon befuellt, kein doppelter Eintrag noetig */ }
                case NO_MATCH -> unmatched.addAll(group);
            }
        }

        log.info("XpengChargeMatcher: car={} enriched={} unmatched={}",
                carId, enriched, unmatched.size());
        return new MatchResult(enriched, unmatched);
    }

    private enum GroupOutcome { ENRICHED, SKIPPED, NO_MATCH }

    private GroupOutcome tryEnrichGroup(UUID carId, int odoKm, List<DetectedChargingSession> group) {
        // Zeitbereich der Gruppe
        LocalDateTime earliestStart = group.stream()
                .map(DetectedChargingSession::startedAt)
                .min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime latestEnd = group.stream()
                .map(DetectedChargingSession::endedAt)
                .max(LocalDateTime::compareTo).orElseThrow();

        List<EvLog> candidates = evLogRepository.findChargeMatchCandidates(
                carId, odoKm - 1, odoKm + 1,
                earliestStart.minus(TIME_SLACK), latestEnd.plus(TIME_SLACK));

        if (candidates.isEmpty()) return GroupOutcome.NO_MATCH;

        // Tie-Breaker: zeitlich naechster Log zur fruehesten Session-Start-Zeit
        EvLog best = candidates.get(0);
        long bestDelta = Math.abs(Duration.between(best.getLoggedAt(), earliestStart).toSeconds());
        for (int i = 1; i < candidates.size(); i++) {
            EvLog c = candidates.get(i);
            long delta = Math.abs(Duration.between(c.getLoggedAt(), earliestStart).toSeconds());
            if (delta < bestDelta) {
                best = c;
                bestDelta = delta;
            }
        }

        if (best.getKwhAtVehicle() != null) {
            // Anderer Job hat den Log schon angereichert - nichts tun, aber auch nicht doppelt anlegen.
            log.debug("XpengChargeMatcher: log {} hat bereits kwhAtVehicle - skip {} Sessions",
                    best.getId(), group.size());
            return GroupOutcome.SKIPPED;
        }

        BigDecimal kwhSum = BigDecimal.ZERO;
        for (DetectedChargingSession s : group) {
            kwhSum = kwhSum.add(s.kwhCharged());
        }
        kwhSum = kwhSum.setScale(4, RoundingMode.HALF_UP);

        // Max-Power: groesster maxPower-Wert der Gruppe (z.B. erste Session 3.9 kW, zweite 10.1 kW -> 10.1)
        BigDecimal groupMaxPower = group.stream()
                .map(DetectedChargingSession::maxPowerKw)
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);

        ChargingType inferredType = inferChargingType(group, groupMaxPower);

        EvLog patched = best.toBuilder()
                .kwhAtVehicle(kwhSum)
                .maxChargingPowerKw(best.getMaxChargingPowerKw() != null
                        ? best.getMaxChargingPowerKw()
                        : groupMaxPower)
                .chargingType(best.getChargingType() != null && best.getChargingType() != ChargingType.UNKNOWN
                        ? best.getChargingType()
                        : inferredType)
                .updatedAt(LocalDateTime.now())
                .build();
        evLogRepository.save(patched);

        // telemetry_extras: erste Session mit nicht-leerem extras-Map gewinnt (per-row-Aggregation
        // ueber mehrere Sub-Sessions ist nicht trivial - vereinfacht).
        Map<String, Object> extrasSource = group.stream()
                .map(DetectedChargingSession::telemetryExtras)
                .filter(m -> m != null && !m.isEmpty())
                .findFirst().orElse(null);
        String extrasJson = serializeExtras(extrasSource);
        if (extrasJson != null) {
            try {
                evLogRepository.updateTelemetryExtrasById(best.getId(), extrasJson);
            } catch (Exception e) {
                log.warn("XpengChargeMatcher: telemetry_extras update failed for log {}",
                        best.getId(), e);
            }
        }
        return GroupOutcome.ENRICHED;
    }

    private static ChargingType inferChargingType(List<DetectedChargingSession> group, BigDecimal maxPower) {
        // Wenn alle Sessions identischen Type haben, dominiere; sonst leite aus max-Power ab.
        String firstType = null;
        boolean uniform = true;
        for (DetectedChargingSession s : group) {
            String t = s.chargingType();
            if (firstType == null) firstType = t;
            else if (!java.util.Objects.equals(firstType, t)) { uniform = false; break; }
        }
        if (uniform && firstType != null && !"UNKNOWN".equalsIgnoreCase(firstType)) {
            try { return ChargingType.valueOf(firstType); } catch (IllegalArgumentException ignored) {}
        }
        if (maxPower != null) {
            return DetectedChargingSession.classifyChargingType(maxPower).equals("DC")
                    ? ChargingType.DC : ChargingType.AC;
        }
        return null;
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
