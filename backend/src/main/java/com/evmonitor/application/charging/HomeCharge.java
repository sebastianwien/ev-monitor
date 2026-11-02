package com.evmonitor.application.charging;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A normal charge at home, overnight at the wallbox from 20 to 80 %: the one rule behind the
 * personal range calculator and the cost tile of the model page.
 */
public final class HomeCharge {

    /** Share of the net battery a home charge refills: 20 → 80 %. */
    public static final BigDecimal SHARE = new BigDecimal("0.6");

    private HomeCharge() {
    }

    /** kWh drawn at the wallbox for one charge: 60 % of the net battery through AC losses; null if an input is missing. */
    public static BigDecimal plugKwh(BigDecimal netKwh, BigDecimal acEfficiency) {
        if (netKwh == null || netKwh.signum() <= 0 || acEfficiency == null || acEfficiency.signum() <= 0) return null;
        return netKwh.multiply(SHARE).divide(acEfficiency, 2, RoundingMode.HALF_UP);
    }
}
