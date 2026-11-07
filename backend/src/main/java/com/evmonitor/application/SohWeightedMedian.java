package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Hub-gewichteter Median über Kapazitätsschätzungen, gemeinsam für die SoH-Quellen CHARGE_LOG und
 * VEHICLE_COUNTER. Jede Schätzung zählt mit ihrem SoC-Hub als Gewicht, so überstimmt ein großer Hub
 * einen kleinen. Median statt Mittelwert, damit eine einzelne kaputte Schätzung das Ergebnis nicht zieht.
 */
final class SohWeightedMedian {

    private static final BigDecimal TWO = new BigDecimal("2");

    /** Eine Kapazitätsschätzung mit dem SoC-Hub, aus dem sie stammt. */
    record Estimate(BigDecimal capacityKwh, BigDecimal socHub) {}

    private SohWeightedMedian() {}

    /**
     * Schätzungen aufsteigend nach Kapazität, die erste, an der das kumulierte Gewicht die Hälfte
     * erreicht. Bei gleichen Hüben ist das der klassische Median.
     *
     * @param estimates nicht leer
     */
    static Estimate of(List<Estimate> estimates) {
        List<Estimate> sorted = estimates.stream()
                .sorted(Comparator.comparing(Estimate::capacityKwh))
                .toList();
        BigDecimal totalWeight = sorted.stream()
                .map(Estimate::socHub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cumulative = BigDecimal.ZERO;
        for (Estimate estimate : sorted) {
            cumulative = cumulative.add(estimate.socHub());
            if (cumulative.multiply(TWO).compareTo(totalWeight) >= 0) return estimate;
        }
        return sorted.get(sorted.size() - 1);
    }
}
