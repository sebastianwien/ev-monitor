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
        LocalDateTime loggedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static EvLogResponse fromDomain(EvLog evLog) {
        return new EvLogResponse(
                evLog.getId(),
                evLog.getCarId(),
                evLog.getKwhCharged(),
                evLog.getCostEur(),
                evLog.getChargeDurationMinutes(),
                evLog.getGeohash(),
                evLog.getOdometerKm(),
                evLog.getMaxChargingPowerKw(),
                evLog.getLoggedAt(),
                evLog.getCreatedAt(),
                evLog.getUpdatedAt());
    }
}
