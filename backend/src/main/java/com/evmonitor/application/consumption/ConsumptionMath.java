package com.evmonitor.application.consumption;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rein mathematische Hilfsfunktionen für Verbrauchsstatistiken.
 *
 * <p>Bewusst als stateless Static-Utility extrahiert, damit der {@code EvLogService}
 * von diesem Rechen-Kleinkram entkoppelt wird und die Funktionen ohne Spring-Kontext
 * gezielt getestet werden können. Die <b>Verbrauchsformeln</b> selbst bleiben
 * weiterhin in {@code EvLogService} - diese Klasse enthält nur statistische
 * Building Blocks (Mean, StdDev, distanzgewichteter Durchschnitt).
 */
public final class ConsumptionMath {

    private ConsumptionMath() {
        // utility class
    }

    /**
     * Arithmetisches Mittel mit 6 Nachkommastellen Zwischenpräzision.
     * Voraussetzung: {@code values} ist nicht leer. Caller muss das prüfen.
     */
    public static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), 6, RoundingMode.HALF_UP);
    }

    /**
     * Standardabweichung (Population, nicht Stichprobe).
     * Verwendet {@link MathContext} mit 10 Stellen für die Wurzel.
     */
    public static BigDecimal stdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSquares = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = sumSquares.divide(new BigDecimal(values.size()), 10, RoundingMode.HALF_UP);
        return variance.sqrt(new MathContext(10, RoundingMode.HALF_UP));
    }

    /**
     * Distanzgewichteter Durchschnitt. Liefert {@code null} wenn keine Daten vorhanden sind.
     */
    public static BigDecimal weightedAverage(BigDecimal totalWeighted, int totalKm) {
        if (totalKm == 0) return null;
        return totalWeighted.divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP);
    }
}
