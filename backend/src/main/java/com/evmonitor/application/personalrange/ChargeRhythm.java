package com.evmonitor.application.personalrange;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How often the reader plugs in, in words. Same table as frontend utils/chargeRhythm.ts, so the
 * result mail says what the model page says:
 * over 1 a week "2,3× pro Woche", 0.5 to 1 "alle 10 Tage", below 0.5 "alle 3 Wochen" (at least 2),
 * without home charging the stops rounded up "2 Ladestopps pro Woche".
 */
public final class ChargeRhythm {

    private ChargeRhythm() {}

    /** @param lang "de" or "en" */
    public static String text(BigDecimal chargesPerWeek, boolean homeCharging, String lang) {
        boolean en = "en".equals(lang);
        double perWeek = chargesPerWeek.doubleValue();
        if (perWeek <= 0) return en ? "no charging needed" : "kein Laden nötig";
        if (!homeCharging) {
            int stops = (int) Math.ceil(perWeek);
            if (en) return stops + (stops == 1 ? " charging stop a week" : " charging stops a week");
            return stops + (stops == 1 ? " Ladestopp pro Woche" : " Ladestopps pro Woche");
        }
        if (perWeek > 1) {
            String n = chargesPerWeek.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return en ? n + "× a week" : n.replace('.', ',') + "× pro Woche";
        }
        if (perWeek >= 0.5) {
            int days = (int) Math.floor(7 / perWeek);
            return en ? "every " + days + " days" : "alle " + days + " Tage";
        }
        int weeks = Math.max(2, (int) Math.floor(1 / perWeek));
        return en ? "every " + weeks + " weeks" : "alle " + weeks + " Wochen";
    }
}
