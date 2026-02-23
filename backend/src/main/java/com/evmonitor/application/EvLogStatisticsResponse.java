package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvLogStatisticsResponse(
        // Key Metrics
        BigDecimal totalDistanceKm,
        BigDecimal averageConsumptionKwhPer100km,
        BigDecimal bestConsumptionKwhPer100km,
        BigDecimal worstConsumptionKwhPer100km,
        Integer totalDrives,

        // WLTP Comparison
        BigDecimal wltpRangeKm,
        BigDecimal wltpConsumptionKwhPer100km,
        BigDecimal wltpDifferencePercent, // Positive = worse than WLTP, Negative = better

        // Consumption Over Time (for chart)
        List<ConsumptionDataPoint> consumptionOverTime
) {
    public record ConsumptionDataPoint(
            LocalDateTime timestamp,
            BigDecimal consumptionKwhPer100km,
            BigDecimal outsideTempC
    ) {}
}
