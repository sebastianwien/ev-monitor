package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for the live charging endpoint.
 * All fields except {@code isActive} may be null when no session is active,
 * when the connectors-service cannot be reached, or when the source (Tesla,
 * Polestar, Smartcar) does not deliver the value.
 *
 * <p>{@code updateIntervalSeconds} ist der Takt der Quelle; das Frontend leitet
 * daraus ab, ab wann die Anzeige als veraltet gilt.
 *
 * <p>Zeitstempel sind {@link Instant} (UTC), damit Jackson sie mit Z-Suffix
 * serialisiert - sonst interpretiert das Frontend naked-LocalDateTime als
 * Browser-Lokal-Zeit und produziert je nach TZ einen Stunden-Versatz in
 * Session-Dauer und Start-Anzeige.
 *
 * <p>{@code sessionEndedAt} ist nur in der 30-min-Grace-Period nach Session-
 * Ende gesetzt: das Frontend zeigt die fertige Kurve weiter, der Stale-
 * Indikator wird durch ein "Beendet vor X min"-Label ersetzt.
 */
public record LiveChargingResponse(
        boolean isActive,
        String chargingType,
        BigDecimal powerKw,
        BigDecimal socPercent,
        BigDecimal energyRemainingKwh,
        Integer timeToFullMinutes,
        BigDecimal estRangeKm,
        BigDecimal chargeAmps,
        Instant sessionStartedAt,
        Instant sessionEndedAt,
        BigDecimal socAtSessionStart,
        Integer chargeLimitSoc,
        Instant lastUpdatedAt,
        BigDecimal energyAddedKwh,
        int updateIntervalSeconds
) {

    /** Inactive placeholder - returned when no session is running or connectors unreachable. */
    public static LiveChargingResponse inactive() {
        return new LiveChargingResponse(false, null, null, null, null, null, null, null, null, null, null, null, null,
                null, 0);
    }
}
