package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
                @PastOrPresent LocalDateTime loggedAt, // Optional: when the charge happened
                Boolean ocrUsed,        // Optional: whether OCR was used to fill in data (+2 bonus coins)
                ChargingType chargingType, // Optional: AC or DC
                RouteType routeType,    // Optional: CITY, COMBINED, or HIGHWAY
                TireType tireType,      // Optional: SUMMER, ALL_YEAR, or WINTER
                Boolean isPublicCharging, // Optional: whether charged at a public CPO
                @Size(max = 100) String cpoName) { // Optional: CPO name (e.g. IONITY, EnBW)

    // Backward-compatible constructor for existing callers (tests)
    public EvLogRequest(UUID carId, BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, Double latitude, Double longitude,
            Integer odometerKm, BigDecimal maxChargingPowerKw, Integer socAfterChargePercent,
            LocalDateTime loggedAt, Boolean ocrUsed, ChargingType chargingType,
            RouteType routeType, TireType tireType) {
        this(carId, kwhCharged, costEur, chargeDurationMinutes, latitude, longitude,
                odometerKm, maxChargingPowerKw, socAfterChargePercent, loggedAt,
                ocrUsed, chargingType, routeType, tireType, null, null);
    }
}
