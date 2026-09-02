package com.evmonitor.application.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Kumuliert die Heimlade-Ersparnis Jahr fuer Jahr aus den tatsaechlichen Logs.
 *
 * Jedes Jahr rechnet mit seinen eigenen kWh, seinen eigenen Kosten und dem oeffentlichen
 * Preisniveau jenes Jahres. Eine Hochrechnung der aktuellen Ersparnis ueber die gesamte
 * Nutzungsdauer waere zweifach falsch: sie unterstellt, es sei immer schon daheim geladen
 * worden, und sie unterstellt konstante Preise ueber die Energiekrise hinweg.
 */
public final class YearlySavingsCalculator {

    private static final int CENTS = 2;

    private YearlySavingsCalculator() {}

    public static List<YearlySaving> cumulate(List<HomeChargingYear> years) {
        if (years == null || years.isEmpty()) return List.of();

        List<YearlySaving> result = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO.setScale(CENTS);

        List<HomeChargingYear> sorted = years.stream()
                .filter(y -> y != null && y.publicPricePerKwh() != null
                        && y.homeKwh() != null && y.paidEur() != null)
                .sorted(Comparator.comparingInt(HomeChargingYear::year))
                .toList();

        for (HomeChargingYear y : sorted) {
            BigDecimal would = y.homeKwh().multiply(y.publicPricePerKwh()).setScale(CENTS, RoundingMode.HALF_UP);
            BigDecimal paid = y.paidEur().setScale(CENTS, RoundingMode.HALF_UP);
            BigDecimal savings = would.subtract(paid);
            cumulative = cumulative.add(savings);
            result.add(new YearlySaving(y.year(), y.homeKwh(), paid, would, savings, cumulative));
        }
        return result;
    }
}
