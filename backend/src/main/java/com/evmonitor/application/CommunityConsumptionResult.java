package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Result of a community consumption calculation.
 *
 * @param value              distance-weighted avg consumption in kWh/100km, null if no data
 * @param minValue           P10 of per-driver or per-trip consumptions, null if insufficient data
 * @param maxValue           P90 of per-driver or per-trip consumptions, null if insufficient data
 * @param rangeSource        whether min/max are based on per-driver or per-trip data, null if no range
 * @param tripCount          number of plausible trips used in the calculation
 * @param estimatedTripCount number of trips calculated via kWh/distance fallback (without SoC)
 */
public record CommunityConsumptionResult(BigDecimal value, BigDecimal minValue, BigDecimal maxValue, RangeSource rangeSource, int tripCount, int estimatedTripCount) {

    public enum RangeSource { PER_DRIVER, PER_TRIP }

    public static final CommunityConsumptionResult EMPTY = new CommunityConsumptionResult(null, null, null, null, 0, 0);
}
