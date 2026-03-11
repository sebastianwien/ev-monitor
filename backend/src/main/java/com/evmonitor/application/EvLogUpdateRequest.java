package com.evmonitor.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

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
        LocalDateTime loggedAt) {
}
