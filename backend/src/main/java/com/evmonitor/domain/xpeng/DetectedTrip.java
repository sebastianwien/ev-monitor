package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record DetectedTrip(
        Instant startedAt,
        Instant endedAt,
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
