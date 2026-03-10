package com.evmonitor.application;

import com.evmonitor.domain.EvLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogResponse(
        UUID id,
        UUID carId,
        BigDecimal kwhCharged,
        BigDecimal costEur,
        Integer chargeDurationMinutes,
        String geohash,
        Integer odometerKm,
        BigDecimal maxChargingPowerKw,
        Integer socAfterChargePercent,
        LocalDateTime loggedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer odometerSuggestionMinKm,
        Integer odometerSuggestionMaxKm,
        Double temperatureCelsius,
        BigDecimal consumptionKwhPer100km,   // null when no previous log with odometer+SoC
        Boolean consumptionImplausible,      // null when consumption not computed, true when flagged
        Integer distanceSinceLastChargeKm) { // null when no previous log with odometer data

    public static EvLogResponse fromDomain(EvLog evLog) {
        return fromDomain(evLog, null, null);
    }

    public static EvLogResponse fromDomain(EvLog evLog, ConsumptionResult consumption) {
        return fromDomain(evLog, consumption, null);
    }

    public static EvLogResponse fromDomain(EvLog evLog, ConsumptionResult consumption, Integer distanceKm) {
        return new EvLogResponse(
                evLog.getId(),
                evLog.getCarId(),
                evLog.getKwhCharged(),
                evLog.getCostEur(),
                evLog.getChargeDurationMinutes(),
                evLog.getGeohash(),
                evLog.getOdometerKm(),
                evLog.getMaxChargingPowerKw(),
                evLog.getSocAfterChargePercent(),
                evLog.getLoggedAt(),
                evLog.getCreatedAt(),
                evLog.getUpdatedAt(),
                evLog.getOdometerSuggestionMinKm(),
                evLog.getOdometerSuggestionMaxKm(),
                evLog.getTemperatureCelsius(),
                consumption != null ? consumption.value() : null,
                consumption != null ? !consumption.plausible() : null,
                distanceKm);
    }
}
