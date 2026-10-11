package com.evmonitor.application.personalrange;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Model data the personal calculator works with. Community values (consumption, charging power,
 * prices) are null when the community has too little data; the calculator then falls back to
 * WLTP factors or reference prices and marks the value as estimated.
 *
 * @param netBatteryKwh        net capacity of the largest variant
 * @param netBatteryMeasured   false when derived as nominal × 0.9 (no net value in the spec)
 * @param highwayCarCount      distinct cars behind highwayKwhPer100km; decides how much of it is used
 * @param baseKwhPer100km      community consumption without highway share (city, else overall)
 */
@Builder(toBuilder = true)
public record ModelRangeBasis(
        BigDecimal netBatteryKwh,
        boolean netBatteryMeasured,
        BigDecimal wltpKwhPer100km,
        BigDecimal summerKwhPer100km,
        BigDecimal winterKwhPer100km,
        BigDecimal highwayKwhPer100km,
        int highwayCarCount,
        BigDecimal baseKwhPer100km,
        BigDecimal dcChargingPowerKw,
        BigDecimal homePricePerKwh,
        BigDecimal publicPricePerKwh,
        BigDecimal referenceHomePricePerKwh,
        BigDecimal referencePublicPricePerKwh
) {}
