package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Result of a community consumption calculation.
 *
 * @param value              distance-weighted avg consumption in kWh/100km, null if no data
 * @param minValue           lowest per-car distance-weighted avg, null if fewer than 2 cars with data
 * @param maxValue           highest per-car distance-weighted avg, null if fewer than 2 cars with data
 * @param tripCount          number of plausible trips used in the calculation
 * @param estimatedTripCount number of trips calculated via kWh/distance fallback (without SoC)
 */
public record CommunityConsumptionResult(BigDecimal value, BigDecimal minValue, BigDecimal maxValue, int tripCount, int estimatedTripCount) {

    public static final CommunityConsumptionResult EMPTY = new CommunityConsumptionResult(null, null, null, 0, 0);
}
