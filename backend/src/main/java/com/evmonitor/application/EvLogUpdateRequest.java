package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socAfterChargePercent,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal socBeforeChargePercent,
        @Positive @DecimalMax("200.0") BigDecimal kwhAtVehicle,
        LocalDateTime loggedAt,
        ChargingType chargingType,
        RouteType routeType,
        TireType tireType,
        Boolean isPublicCharging,
        @Size(max = 100) String cpoName,
        BigDecimal costExchangeRate,
        @Size(max = 3) String costCurrency,
        UUID chargingProviderId) {

    // Backward-compatible constructor for existing callers (tests)
    public EvLogUpdateRequest(BigDecimal kwhCharged, BigDecimal costEur,
            Integer chargeDurationMinutes, Double latitude, Double longitude,
            Integer odometerKm, BigDecimal maxChargingPowerKw,
            BigDecimal socAfterChargePercent, BigDecimal socBeforeChargePercent,
            LocalDateTime loggedAt, ChargingType chargingType,
            RouteType routeType, TireType tireType) {
        this(kwhCharged, costEur, chargeDurationMinutes, latitude, longitude,
                odometerKm, maxChargingPowerKw, socAfterChargePercent, socBeforeChargePercent,
                null, loggedAt, chargingType, routeType, tireType, null, null, null, null, null);
    }
}
