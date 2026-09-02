package com.evmonitor.application.savings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Ersparnis ist eine kontrafaktische Aussage: "haettest du dieselben kWh oeffentlich
 * geladen". Sie wird ueber die gesamte Zeit gerechnet, in der daheim geladen wurde - eine
 * Beschraenkung auf die letzten zwoelf Monate wuerde alles davor unsichtbar machen.
 *
 * Fuer die Restlaufzeit braucht es trotzdem eine Rate. Die entsteht aus der Gesamtersparnis
 * geteilt durch die bisherige Laufzeit in Monaten - breiter abgestuetzt als ein einzelnes
 * Jahr und unempfindlich gegen einen schwachen Winter.
 */
class ChargingSavingsCalculatorTest {

    private static BigDecimal eur(String s) { return new BigDecimal(s); }

    private static PriceBasis home() { return new PriceBasis(PriceSource.OWN_LOGS, eur("0.27"), 23); }
    private static PriceBasis pub() { return new PriceBasis(PriceSource.OWN_PUBLIC, eur("0.40"), 18); }

    private static YearlySaving year(int y, String kwh, String paid, String would, String cumulative) {
        return new YearlySaving(y, eur(kwh), eur(paid), eur(would),
                eur(would).subtract(eur(paid)), eur(cumulative));
    }

    /** Zwei Jahre, 24 Monate Laufzeit. */
    private static List<YearlySaving> twoYears() {
        return List.of(
                year(2025, "300", "84.00", "165.00", "81.00"),
                year(2026, "640", "172.80", "256.00", "164.20"));
    }

    @Test
    void totals_spanAllYears_notJustTheLastTwelveMonths() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), null, eur("24"));

        assertEquals(0, eur("940").compareTo(s.homeKwh()));
        assertEquals(0, eur("256.80").compareTo(s.actuallyPaidEur()));
        assertEquals(0, eur("421.00").compareTo(s.wouldHaveCostEur()));
        assertEquals(0, eur("164.20").compareTo(s.savingsEur()));
    }

    /** Die angezeigten ct-Preise sind Mischwerte ueber die gesamte Zeit. */
    @Test
    void displayedPrices_areBlendedOverTheWholePeriod() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), null, eur("24"));

        // 256,80 / 940 = 0,2732   421,00 / 940 = 0,4479
        assertEquals(0, eur("0.2732").compareTo(s.homePrice().pricePerKwh()));
        assertEquals(0, eur("0.4479").compareTo(s.publicPrice().pricePerKwh()));
    }

    /** Die Herkunft der Vergleichszahl bleibt erhalten - sie traegt die Basiszeile. */
    @Test
    void priceBasis_keepsItsSourceAndSampleSize() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), null, eur("24"));

        assertEquals(PriceSource.OWN_PUBLIC, s.publicPrice().source());
        assertEquals(18, s.publicPrice().sampleSize());
    }

    // ------------------------------------------------------------- Amortisation

    /**
     * Nenner ist der Monatsdurchschnitt der gesamten Laufzeit: 164,20 EUR auf 24 Monate
     * sind 6,84 EUR im Monat. Von 1.000 EUR bleiben 835,80 offen - das sind rund 122
     * Monate oder gut zehn Jahre.
     */
    @Test
    void remainingTime_usesTheMonthlyAverageOfTheWholePeriod() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), eur("1000"), eur("24"));

        assertEquals(0, eur("164.20").compareTo(s.recoveredEur()));
        assertEquals(10.2, s.amortisationYearsRemaining().doubleValue(), 0.1);
        assertFalse(s.fullyAmortised());
    }

    /** Kurze Laufzeit, hohe Ersparnis: die Rate faellt entsprechend hoch aus. */
    @Test
    void shortPeriod_yieldsAHigherMonthlyRate() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                List.of(year(2026, "640", "0.00", "256.00", "256.00")),
                home(), pub(), eur("1000"), eur("6"));

        // 256 EUR in 6 Monaten = 42,67 EUR/Monat, 744 offen -> rund 17,4 Monate
        assertEquals(1.5, s.amortisationYearsRemaining().doubleValue(), 0.1);
    }

    @Test
    void recoveredExceedsInvestment_isFullyAmortised() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), eur("100"), eur("24"));

        assertTrue(s.fullyAmortised());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.amortisationYearsRemaining()));
    }

    @Test
    void withoutInvestment_noAmortisation() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), null, eur("24"));

        assertNull(s.investmentEur());
        assertNull(s.amortisationYearsRemaining());
    }

    /** Ohne Ersparnis amortisiert nichts - und es wird nicht durch null geteilt. */
    @Test
    void zeroSavings_amortisationIsUnknown() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                List.of(year(2026, "640", "256.00", "256.00", "0.00")),
                home(), pub(), eur("1000"), eur("12"));

        assertEquals(0, BigDecimal.ZERO.compareTo(s.savingsEur()));
        assertNull(s.amortisationYearsRemaining());
    }

    /** Ohne Laufzeit gibt es keine Rate - dann keine Prognose statt einer Division durch null. */
    @Test
    void zeroMonths_amortisationIsUnknown() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                twoYears(), home(), pub(), eur("1000"), BigDecimal.ZERO);

        assertNull(s.amortisationYearsRemaining());
    }

    @Test
    void withoutAnyYear_noResult() {
        assertNull(ChargingSavingsCalculator.calculate(List.of(), home(), pub(), null, eur("24")));
    }

    /** Ein teurer Haustarif ergibt eine negative Ersparnis - die wird ausgewiesen. */
    @Test
    void moreExpensiveAtHome_reportsNegativeSavings() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                List.of(year(2026, "500", "225.00", "200.00", "-25.00")),
                home(), pub(), null, eur("12"));

        assertTrue(s.savingsEur().signum() < 0);
    }
}
