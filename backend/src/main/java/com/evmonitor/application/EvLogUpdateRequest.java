package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request body for updating an existing EvLog.
 * All fields are optional — null means "keep existing value".
 * To set a new location, provide latitude + longitude (converted to geohash server-side).
 */
public record EvLogUpdateRequest(
        @Positive BigDecimal kwhCharged,
        BigDecimal costEur,
        @Positive Integer chargeDurationMinutes,
        Double latitude,
        Double longitude,
        Integer odometerKm,
        BigDecimal maxChargingPowerKw,
        @Min(0) @Max(100) Integer socAfterChargePercent,
        @Min(0) @Max(100) Integer socBeforeChargePercent,
        @PastOrPresent LocalDateTime loggedAt,
        ChargingType chargingType,
        RouteType routeType,
        TireType tireType,
        Boolean isPublicCharging,
        @Size(max = 100) String cpoName,
        BigDecimal costExchangeRate,
        @Size(max = 3) String costCurrency) {

    // Backward-compatible constructor for existing callers (tests)
    public EvLogUpdateRequest(BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, Double latitude, Double longitude,
            Integer odometerKm, BigDecimal maxChargingPowerKw,
            Integer socAfterChargePercent, Integer socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType,
            RouteType routeType, TireType tireType) {
        this(kwhCharged, costEur, chargeDurationMinutes, latitude, longitude,
                odometerKm, maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent,
                loggedAt, chargingType, routeType, tireType, null, null, null, null);
    }
}
