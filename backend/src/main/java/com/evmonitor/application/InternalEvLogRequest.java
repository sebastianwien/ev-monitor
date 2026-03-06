package com.evmonitor.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for log creation by internal services (e.g., Wallbox OCPP Service).
 * Unlike the user-facing EvLogRequest, cost is optional (wallbox doesn't know tariff).
 */
public record InternalEvLogRequest(
        @NotNull UUID carId,
        @NotNull UUID userId,
        @NotNull @Positive BigDecimal kwhCharged,
        @NotNull @Positive Integer chargeDurationMinutes,
        @NotNull LocalDateTime loggedAt,
        String geohash,
        Integer odometerSuggestionMinKm,
        Integer odometerSuggestionMaxKm,
        String dataSource,  // optional: WALLBOX_OCPP | WALLBOX_GOE | TESLA_FLEET (defaults to WALLBOX_OCPP)
        BigDecimal costEur) {  // optional: only available from sources that report cost (e.g. Tesla Supercharger)
}
