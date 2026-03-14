package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
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
        Integer socBeforeChargePercent,
        LocalDateTime loggedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer odometerSuggestionMinKm,
        Integer odometerSuggestionMaxKm,
        Double temperatureCelsius,
        BigDecimal consumptionKwhPer100km,   // null when no previous log with odometer+SoC
        Boolean consumptionImplausible,      // null when consumption not computed, true when flagged
        Integer distanceSinceLastChargeKm,   // null when no previous log with odometer data
        Boolean consumptionIsEstimated,      // true when calculated via kWh/distance fallback (no SoC)
        ChargingType chargingType,           // AC, DC, or UNKNOWN
        RouteType routeType,                 // Optional: CITY, COMBINED, or HIGHWAY
        TireType tireType) {                 // Optional: SUMMER, ALL_YEAR, or WINTER

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
                evLog.getSocBeforeChargePercent(),
                evLog.getLoggedAt(),
                evLog.getCreatedAt(),
                evLog.getUpdatedAt(),
                evLog.getOdometerSuggestionMinKm(),
                evLog.getOdometerSuggestionMaxKm(),
                evLog.getTemperatureCelsius(),
                consumption != null ? consumption.value() : null,
                consumption != null ? !consumption.plausible() : null,
                distanceKm,
                consumption != null ? consumption.estimated() : null,
                evLog.getChargingType(),
                evLog.getRouteType(),
                evLog.getTireType());
    }
}
