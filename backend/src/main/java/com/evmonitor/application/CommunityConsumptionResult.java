package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Result of a community consumption calculation.
 *
 * @param value     distance-weighted avg consumption in kWh/100km, null if no data
 * @param tripCount number of plausible trips used in the calculation
 */
public record CommunityConsumptionResult(BigDecimal value, int tripCount) {

    public static final CommunityConsumptionResult EMPTY = new CommunityConsumptionResult(null, 0);
}
