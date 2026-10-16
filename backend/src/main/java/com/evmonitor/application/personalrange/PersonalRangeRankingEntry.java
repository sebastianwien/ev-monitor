package com.evmonitor.application.personalrange;

import java.math.BigDecimal;

/**
 * One model of the model ranking for the reader's profile, from the same calculation as the model
 * page (largest WLTP battery). The stop ladder (reach without a stop, with one and two stops) does not depend on the
 * profile, it rests on the model's highway consumption like the stops of the long trip.
 */
public record PersonalRangeRankingEntry(
        String brand,
        String model,
        int summerRangeKm,
        int winterRangeKm,
        int longTripStops,
        /** null without a reliable charging power, like on the model page */
        Integer longTripTotalMinutes,
        BigDecimal chargesPerWeekSummer,
        BigDecimal chargesPerWeekWinter,
        int monthlyCostEur,
        /** the distance a month the cost rests on: yearly km / 12, else the commute */
        int monthlyKm,
        int zeroStopReachKm,
        int oneStopReachKm,
        int twoStopsReachKm
) {}
