package com.evmonitor.application.savings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kumulierte Ersparnis je Jahr, aus den tatsaechlichen Logs.
 *
 * Vorher wurde die Ersparnis der letzten zwoelf Monate ueber die gesamte Nutzungsdauer
 * hochgerechnet. Das war in zwei Punkten falsch: es unterstellte, der Nutzer haette immer
 * schon daheim geladen (auf Prod lagen dazwischen bis zu 3,4 Jahre), und es unterstellte
 * konstante Preise - ausgerechnet ueber die Energiekrise hinweg.
 *
 * Jedes Jahr wird jetzt mit seinen eigenen kWh, seinen eigenen Kosten und dem
 * oeffentlichen Preisniveau jenes Jahres gerechnet.
 */
class YearlySavingsCalculatorTest {

    private static BigDecimal eur(String s) { return new BigDecimal(s); }

    private static HomeChargingYear year(int y, String kwh, String paid, String publicPrice) {
        return new HomeChargingYear(y, eur(kwh), eur(paid), eur(publicPrice));
    }

    @Test
    void savingsPerYear_useThatYearsOwnPrices() {
        List<YearlySaving> result = YearlySavingsCalculator.cumulate(List.of(
                year(2025, "300", "84.00", "0.55"),   // Energiekrise: oeffentlich teuer
                year(2026, "839", "242.34", "0.417")
        ));

        assertEquals(2, result.size());
        // 300 * 0,55 = 165,00 - 84,00 = 81,00
        assertEquals(0, eur("81.00").compareTo(result.get(0).savingsEur()));
        // 839 * 0,417 = 349,86 - 242,34 = 107,52
        assertEquals(0, eur("107.52").compareTo(result.get(1).savingsEur()));
    }

    @Test
    void cumulative_addsUpAcrossYears() {
        List<YearlySaving> result = YearlySavingsCalculator.cumulate(List.of(
                year(2025, "300", "84.00", "0.55"),
                year(2026, "839", "242.34", "0.417")
        ));

        assertEquals(0, eur("81.00").compareTo(result.get(0).cumulativeEur()));
        assertEquals(0, eur("188.52").compareTo(result.get(1).cumulativeEur()));
    }

    /** Ein Jahr, in dem oeffentlich billiger war als daheim, zieht die Summe nach unten. */
    @Test
    void aYearWithNegativeSavings_reducesTheTotal() {
        List<YearlySaving> result = YearlySavingsCalculator.cumulate(List.of(
                year(2025, "100", "40.00", "0.30"),   // +(-10)
                year(2026, "100", "25.00", "0.40")    // +15
        ));

        assertEquals(0, eur("-10.00").compareTo(result.get(0).savingsEur()));
        assertEquals(0, eur("5.00").compareTo(result.get(1).cumulativeEur()));
    }

    @Test
    void emptyInput_yieldsNothing() {
        assertTrue(YearlySavingsCalculator.cumulate(List.of()).isEmpty());
        assertTrue(YearlySavingsCalculator.cumulate(null).isEmpty());
    }

    /** Ein Jahr ohne bekanntes oeffentliches Preisniveau laesst sich nicht vergleichen
     *  und faellt heraus, statt mit einer geratenen Zahl mitzulaufen. */
    @Test
    void yearWithoutPublicPrice_isSkipped() {
        List<YearlySaving> result = YearlySavingsCalculator.cumulate(List.of(
                new HomeChargingYear(2024, eur("500"), eur("140"), null),
                year(2026, "839", "242.34", "0.417")
        ));

        assertEquals(1, result.size());
        assertEquals(2026, result.get(0).year());
    }

    @Test
    void firstYear_isTheStartOfTheRail() {
        List<YearlySaving> result = YearlySavingsCalculator.cumulate(List.of(
                year(2025, "300", "84.00", "0.55"),
                year(2026, "839", "242.34", "0.417")
        ));

        assertEquals(2025, result.get(0).year());
    }
}
