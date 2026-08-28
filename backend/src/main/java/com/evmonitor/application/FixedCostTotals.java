package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Fixkosten eines Zeitraums, getrennt nach Richtung.
 *
 * @param cost   Summe der Kostenpositionen (brutto, ohne Einnahmen)
 * @param income Summe der Einnahmen, positiv ausgewiesen
 */
public record FixedCostTotals(BigDecimal cost, BigDecimal income) {

    public static final FixedCostTotals ZERO = new FixedCostTotals(BigDecimal.ZERO, BigDecimal.ZERO);

    /** Nettobelastung: Kosten abzueglich Einnahmen. */
    public BigDecimal net() {
        return cost.subtract(income);
    }
}
