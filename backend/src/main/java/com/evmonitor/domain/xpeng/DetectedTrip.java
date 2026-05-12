package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record DetectedTrip(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        BigDecimal odometerStartKm,
        BigDecimal odometerEndKm,
        BigDecimal distanceKm,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal consumedKwh,
        BigDecimal avgSpeedKmh,
        BigDecimal maxSpeedKmh,
        Map<String, Object> telemetryExtras
) {}
