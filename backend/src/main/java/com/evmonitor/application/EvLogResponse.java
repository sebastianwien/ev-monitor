package com.evmonitor.application;

import com.evmonitor.domain.DrivingStyle;
import com.evmonitor.domain.EvLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogResponse(
        UUID id,
        UUID carId,
        BigDecimal distanceKm,
        BigDecimal consumptionKwhPer100km,
        BigDecimal outsideTempC,
        DrivingStyle drivingStyle,
        String geohash,
        LocalDateTime loggedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static EvLogResponse fromDomain(EvLog evLog) {
        return new EvLogResponse(
                evLog.getId(),
                evLog.getCarId(),
                evLog.getDistanceKm(),
                evLog.getConsumptionKwhPer100km(),
                evLog.getOutsideTempC(),
                evLog.getDrivingStyle(),
                evLog.getGeohash(),
                evLog.getLoggedAt(),
                evLog.getCreatedAt(),
                evLog.getUpdatedAt());
    }
}
