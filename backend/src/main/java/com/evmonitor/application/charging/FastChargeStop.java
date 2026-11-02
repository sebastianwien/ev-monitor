package com.evmonitor.application.charging;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A fast-charging stop from 10 to 80 %, the one rule behind the personal range calculator, the
 * partner tile and the charging facts of the model page.
 */
public final class FastChargeStop {

    /** Share of the net battery a stop refills: 10 → 80 %. */
    public static final BigDecimal SHARE = new BigDecimal("0.7");

    private FastChargeStop() {
    }

    /** kWh billed at the charger for one stop: 70 % of the net battery through DC losses; null if an input is missing. */
    public static BigDecimal plugKwh(BigDecimal netKwh, BigDecimal dcEfficiency) {
        BigDecimal exact = exactPlugKwh(netKwh, dcEfficiency);
        return exact != null ? exact.setScale(2, RoundingMode.HALF_UP) : null;
    }

    /** Price of one stop: {@link #plugKwh} at the price per kWh; null if an input is missing. */
    public static BigDecimal cost(BigDecimal netKwh, BigDecimal dcEfficiency, BigDecimal pricePerKwh) {
        BigDecimal exact = exactPlugKwh(netKwh, dcEfficiency);
        if (exact == null || pricePerKwh == null) return null;
        return exact.multiply(pricePerKwh).setScale(2, RoundingMode.HALF_UP);
    }

    /** Minutes of one stop at a charging power, rounded to 5; null without battery or power. */
    public static Integer minutes(BigDecimal netKwh, BigDecimal powerKw) {
        if (netKwh == null || netKwh.signum() <= 0 || powerKw == null || powerKw.signum() <= 0) return null;
        double minutes = netKwh.multiply(SHARE).doubleValue() / powerKw.doubleValue() * 60;
        return (int) (Math.round(minutes / 5.0) * 5);
    }

    private static BigDecimal exactPlugKwh(BigDecimal netKwh, BigDecimal dcEfficiency) {
        if (netKwh == null || netKwh.signum() <= 0 || dcEfficiency == null || dcEfficiency.signum() <= 0) return null;
        return netKwh.multiply(SHARE).divide(dcEfficiency, 6, RoundingMode.HALF_UP);
    }
}
