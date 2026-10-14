package com.evmonitor.application.personalrange;

import java.math.BigDecimal;

/**
 * How often the reader plugs in, in words. Same table as frontend utils/chargeRhythm.ts, so the
 * result mail says what the model page says:
 * 3.5 or more a week "täglich" (7 / L rounded down is 1), 0.5 to 3.5 "alle 10 Tage" (7 / L rounded down), below 0.5 "alle 3 Wochen" (at least 2),
 * without home charging from one a week the charges rounded up "2-mal pro Woche", below that as at home.
 */
public final class ChargeRhythm {

    private ChargeRhythm() {}

    /** @param lang "de" or "en" */
    public static String text(BigDecimal chargesPerWeek, boolean homeCharging, String lang) {
        boolean en = "en".equals(lang);
        double perWeek = chargesPerWeek.doubleValue();
        if (perWeek <= 0) return en ? "no charging needed" : "kein Laden nötig";
        if (!homeCharging && perWeek >= 1) {
            int times = (int) Math.ceil(perWeek);
            if (en) return times == 1 ? "once a week" : times + " times a week";
            return times + "-mal pro Woche";
        }
        if (perWeek >= 0.5) {
            int days = Math.max(1, (int) Math.floor(7 / perWeek));
            if (days == 1) return en ? "every day" : "täglich";
            return en ? "every " + days + " days" : "alle " + days + " Tage";
        }
        int weeks = Math.max(2, (int) Math.floor(1 / perWeek));
        return en ? "every " + weeks + " weeks" : "alle " + weeks + " Wochen";
    }
}
