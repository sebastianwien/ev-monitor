package com.evmonitor.application;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for log creation by internal services (e.g., Wallbox OCPP Service, go-e, Tesla).
 * No @Positive constraints — we prefer storing potentially wrong data over losing it entirely.
 * carId, userId and loggedAt remain required since without them the record is unassignable.
 */
public record InternalEvLogRequest(
        @NotNull UUID carId,
        @NotNull UUID userId,
        BigDecimal kwhCharged,
        Integer chargeDurationMinutes,
        @NotNull LocalDateTime loggedAt,
        String geohash,
        Integer odometerSuggestionMinKm,
        Integer odometerSuggestionMaxKm,
        String dataSource,    // optional: WALLBOX_OCPP | WALLBOX_GOE | TESLA_FLEET_IMPORT | TESLA_LIVE (defaults to WALLBOX_OCPP)
        BigDecimal costEur,   // optional: only available from sources that report cost (e.g. Tesla Supercharger)
        String chargingType,  // optional: AC | DC (defaults to UNKNOWN)
        boolean mergeSessions) {  // optional: if true, merge with adjacent same-day sessions (WALLBOX_GOE only)
}
