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
     *                 BATTERY, CHARGING_POWER, PRICE
     * @param measured true = community data, false = estimate
     * @param value    the value used (kWh/100km, kWh, kW or EUR/kWh)
     */
    public record Assumption(String key, boolean measured, BigDecimal value) {}
}
