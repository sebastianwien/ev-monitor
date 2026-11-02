package com.evmonitor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Was "Reichweite" überall heißt: die Strecke von 100 auf 10 % des Akkus, mit der SoH-adjustierten
 * Kapazität. Einzige Formel; kein Frontend und kein anderer Service rechnet Reichweite selbst.
 */
public final class RangeMath {

    /** Anteil des Akkus im Fenster 100 → 10 %. */
    public static final BigDecimal SHARE = new BigDecimal("0.9");

    private RangeMath() {}

    /** Ganze km; null ohne Kapazität oder ohne positiven Verbrauch. */
    public static Integer rangeKm(BigDecimal effectiveCapacityKwh, BigDecimal consumptionKwhPer100km) {
        if (effectiveCapacityKwh == null || effectiveCapacityKwh.signum() <= 0) return null;
        if (consumptionKwhPer100km == null || consumptionKwhPer100km.signum() <= 0) return null;
        return effectiveCapacityKwh.multiply(SHARE).multiply(BigDecimal.valueOf(100))
                .divide(consumptionKwhPer100km, 0, RoundingMode.HALF_UP).intValue();
    }
}
