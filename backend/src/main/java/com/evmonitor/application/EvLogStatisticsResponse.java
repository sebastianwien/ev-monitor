package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvLogStatisticsResponse(
        // Key Metrics
        BigDecimal totalKwhCharged,
        BigDecimal totalCostEur,
        BigDecimal avgCostPerKwh,
        BigDecimal cheapestChargeEur,
        BigDecimal mostExpensiveChargeEur,
        Integer avgChargeDurationMinutes,
        Integer totalCharges,
        BigDecimal totalDistanceKm,            // null if no odometer data
        BigDecimal avgConsumptionKwhPer100km,  // null if no odometer data
        Integer estimatedConsumptionCount,     // count of logs where consumption is estimated (kWh/distance fallback)

        // Seasonal consumption (null if insufficient data for that season)
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,

        // Charge Over Time (for chart)
        List<ChargeDataPoint> chargesOverTime
) {
    public record ChargeDataPoint(
            LocalDateTime timestamp,
            BigDecimal costEur,
            BigDecimal kwhCharged,
            BigDecimal distanceKm,             // null if no odometer data
            BigDecimal consumptionKwhPer100km  // null if no odometer data
    ) {}
}
