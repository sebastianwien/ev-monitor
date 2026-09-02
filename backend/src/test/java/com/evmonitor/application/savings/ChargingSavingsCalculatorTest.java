package com.evmonitor.application.savings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Ersparnis ist eine kontrafaktische Aussage: "haettest du dieselben kWh oeffentlich
 * geladen". Sie wird deshalb aus drei offen ausgewiesenen Groessen gebildet - geladene
 * kWh, Heimpreis, oeffentlicher Vergleichspreis - und nirgends geschoent.
 */
class ChargingSavingsCalculatorTest {

    private static BigDecimal eur(String s) { return new BigDecimal(s); }

    private static PriceBasis home(String price) {
        return new PriceBasis(PriceSource.OWN_LOGS, eur(price), 12);
    }

    private static PriceBasis pub(String price) {
        return new PriceBasis(PriceSource.COUNTRY, eur(price), 2659);
    }

    /** Jahresreihe mit genau einem Jahr, dessen aufgelaufene Summe {@code cumulative} ist. */
    private static java.util.List<YearlySaving> years(String cumulative) {
        return java.util.List.of(new YearlySaving(2026, eur("640"), eur("172.80"),
                eur("256.00"), eur(cumulative), eur(cumulative)));
    }

    /** Der gemessene Median-Heimlader auf Prod: 640 kWh, 0,27 gegen 0,40 EUR/kWh. */
    @Test
    void medianHomeCharger_yieldsMeasuredSavings() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("640"), home("0.27"), pub("0.40"), null, years("83.20"));

        assertEquals(0, eur("83.20").compareTo(s.savingsEur()));
        assertEquals(0, eur("256.00").compareTo(s.wouldHaveCostEur()));
        assertEquals(0, eur("172.80").compareTo(s.actuallyPaidEur()));
    }

    /** PV-Ueberschuss: der Heimpreis ist null, gespart wird der volle oeffentliche Preis. */
    @Test
    void pvSurplus_savesTheFullPublicPrice() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("1441"), home("0.00"), pub("0.40"), null, years("576.40"));

        assertEquals(0, eur("576.40").compareTo(s.savingsEur()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.actuallyPaidEur()));
    }

    /**
     * Ein teurer Haustarif gegen billiges oeffentliches Laden ergibt eine negative
     * Ersparnis. Die wird ausgewiesen und nicht auf null geklemmt - sonst behauptet die
     * Kachel einen Vorteil, den es nicht gibt.
     */
    @Test
    void moreExpensiveAtHome_reportsNegativeSavings() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("500"), home("0.45"), pub("0.40"), null, years("-25.00"));

        assertTrue(s.savingsEur().signum() < 0);
        assertEquals(0, eur("-25.00").compareTo(s.savingsEur()));
    }

    @Test
    void withoutHomePrice_noResult() {
        assertNull(ChargingSavingsCalculator.calculate(
                eur("640"), PriceBasis.NONE, pub("0.40"), null, years("83.20")));
    }

    @Test
    void withoutPublicPrice_noResult() {
        assertNull(ChargingSavingsCalculator.calculate(
                eur("640"), home("0.27"), PriceBasis.NONE, null, years("83.20")));
    }

    // ------------------------------------------------------------- Amortisation

    /** Ohne hinterlegte Investition gibt es keine Amortisation - die Kachel fragt danach. */
    @Test
    void withoutInvestment_noAmortisation() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("640"), home("0.27"), pub("0.40"), null, years("212.16"));

        assertNull(s.investmentEur());
        assertNull(s.amortisationYearsRemaining());
    }

    /** 212,16 EUR aufgelaufen, 83,20 EUR im Jahr, 1.400 EUR Wallbox -> knapp 14 Jahre Rest. */
    @Test
    void medianHomeCharger_amortisationTakesOverADecade() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("640"), home("0.27"), pub("0.40"), eur("1400"), years("212.16"));

        assertEquals(0, eur("212.16").compareTo(s.recoveredEur()));
        assertTrue(s.amortisationYearsRemaining().doubleValue() > 13);
        assertFalse(s.fullyAmortised());
    }

    @Test
    void recoveredExceedsInvestment_isFullyAmortised() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("1441"), home("0.00"), pub("0.40"), eur("1400"), years("1469.82"));

        assertTrue(s.fullyAmortised());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.amortisationYearsRemaining()));
    }

    /** Ohne Ersparnis amortisiert nichts - und es wird nicht durch null geteilt. */
    @Test
    void zeroSavings_amortisationIsUnknown() {
        ChargingSavings s = ChargingSavingsCalculator.calculate(
                eur("640"), home("0.40"), pub("0.40"), eur("1400"), years("0.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(s.savingsEur()));
        assertNull(s.amortisationYearsRemaining());
        assertFalse(s.fullyAmortised());
    }
}
