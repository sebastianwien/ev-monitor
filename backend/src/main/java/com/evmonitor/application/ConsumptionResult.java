package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Result of a per-trip consumption calculation, including a plausibility verdict.
 *
 * @param value       consumption in kWh/100km
 * @param plausible   false if the value was flagged as an outlier by the plausibility check
 * @param distanceKm  trip distance in km (logX odometer → logY odometer)
 * @param estimated   true if calculated via kWh_charged/distance fallback (no SoC data available)
 */
public record ConsumptionResult(BigDecimal value, boolean plausible, int distanceKm, boolean estimated) {

    /** Convenience constructor for SoC-based results (not estimated). */
    public ConsumptionResult(BigDecimal value, boolean plausible, int distanceKm) {
        this(value, plausible, distanceKm, false);
    }
}
