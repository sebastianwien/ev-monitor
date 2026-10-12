package com.evmonitor.application.charging;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A fast-charging stop from 10 to 80 %, the one rule behind the personal range calculator and the
 * partner tile (the model page mirrors it in modelStory.ts fastStop).
 */
public final class FastChargeStop {

    /** Share of the net battery a stop refills: 10 → 80 %. */
    public static final BigDecimal SHARE = new BigDecimal("0.7");

    private FastChargeStop() {
    }

    /** Price of one stop: 70 % of the net battery through DC losses, billed at the plug; null if an input is missing. */
    public static BigDecimal cost(BigDecimal netKwh, BigDecimal dcEfficiency, BigDecimal pricePerKwh) {
        if (netKwh == null || pricePerKwh == null || dcEfficiency == null || dcEfficiency.signum() <= 0) return null;
        return netKwh.multiply(SHARE).multiply(pricePerKwh).divide(dcEfficiency, 2, RoundingMode.HALF_UP);
    }
}
