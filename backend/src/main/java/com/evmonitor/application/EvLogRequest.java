package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogRequest(
                @NotNull UUID carId,
                @NotNull @Positive BigDecimal kwhCharged,
                @NotNull @Positive BigDecimal costEur,
                @Positive Integer chargeDurationMinutes, // Optional: charging duration in minutes
                Double latitude,  // Optional: for geolocation (not stored, converted to geohash)
                Double longitude, // Optional: for geolocation (not stored, converted to geohash)
                Integer odometerKm, // Optional for imports, required in frontend for manual entry
                BigDecimal maxChargingPowerKw, // Optional: max charging power in kW
                @Min(0) @Max(100) Integer socAfterChargePercent, // Optional for imports, required in frontend for manual entry
                LocalDateTime loggedAt, // Optional: when the charge happened
                Boolean ocrUsed,        // Optional: whether OCR was used to fill in data (+2 bonus coins)
                ChargingType chargingType, // Optional: AC or DC
                RouteType routeType,    // Optional: CITY, COMBINED, or HIGHWAY
                TireType tireType) {    // Optional: SUMMER, ALL_YEAR, or WINTER
}
