package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DetectedChargingSession(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal kwhCharged,
        BigDecimal maxPowerKw,
        BigDecimal odometerKm,
        String chargingType
) {
    public static String classifyChargingType(BigDecimal maxPowerKw) {
        if (maxPowerKw == null) return "UNKNOWN";
        return maxPowerKw.compareTo(new BigDecimal("22")) > 0 ? "DC" : "AC";
    }
}
