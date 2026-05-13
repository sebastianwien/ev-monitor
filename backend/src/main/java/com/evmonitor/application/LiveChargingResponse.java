package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for the live charging endpoint.
 * All fields except {@code isActive} may be null when no session is active
 * or when the connectors-service cannot be reached.
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
        LocalDateTime sessionStartedAt,
        BigDecimal socAtSessionStart,
        Integer chargeLimitSoc,
        LocalDateTime lastUpdatedAt
) {

    /** Inactive placeholder - returned when no session is running or connectors unreachable. */
    public static LiveChargingResponse inactive() {
        return new LiveChargingResponse(false, null, null, null, null, null, null, null, null, null, null, null);
    }
}
