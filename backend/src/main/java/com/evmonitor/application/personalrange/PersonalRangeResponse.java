package com.evmonitor.application.personalrange;

import java.math.BigDecimal;
import java.util.List;

/**
 * Personal calculator result. Every input value the result rests on is listed in assumptions,
 * flagged as measured (community data) or estimated (WLTP factor, reference value).
 */
public record PersonalRangeResponse(
        int winterRangeKm,
        int summerRangeKm,
        int longTripStops,
        int longTripChargingMinutes,
        BigDecimal chargesPerWeek,
        int monthlyCostEur,
        DataLevel dataLevel,
        List<Assumption> assumptions
) {
    /** MEASURED: all consumption values from the community; WLTP_FACTOR: none; PARTIAL: mixed. */
    public enum DataLevel { MEASURED, PARTIAL, WLTP_FACTOR }

    /**
     * @param key      SUMMER_CONSUMPTION, WINTER_CONSUMPTION, HIGHWAY_CONSUMPTION, BASE_CONSUMPTION,
     *                 BATTERY, CHARGING_POWER, PRICE (community or reference price) or OWN_PRICE
     *                 (the user's own price, listed as measured: it is their fact, not an estimate)
     * @param measured true = community data, false = estimate
     * @param value    the value used (kWh/100km, kWh, kW or EUR/kWh)
     * @param measuredPercent share of community data in value: 0, 100, or in between for a blend
     *                 (highway consumption while few cars report it); measured is true only at 100
     */
    public record Assumption(String key, boolean measured, BigDecimal value, int measuredPercent) {
        Assumption(String key, boolean measured, BigDecimal value) {
            this(key, measured, value, measured ? 100 : 0);
        }
    }
}
