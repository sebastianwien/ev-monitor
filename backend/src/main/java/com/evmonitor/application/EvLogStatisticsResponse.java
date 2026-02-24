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

        // Charge Over Time (for chart)
        List<ChargeDataPoint> chargesOverTime
) {
    public record ChargeDataPoint(
            LocalDateTime timestamp,
            BigDecimal costEur,
            BigDecimal kwhCharged
    ) {}
}
