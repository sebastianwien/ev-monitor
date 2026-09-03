package com.evmonitor.application.savings;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Antwort der Ersparnis-Kachel.
 *
 * Gibt nur Aggregate heraus, nie Rohdaten anderer Nutzer - die Stichprobengroesse sagt,
 * wie breit der Vergleich ist, nicht wer darin steckt.
 *
 * Der Rechenweg wird bewusst vollstaendig mitgeliefert. Die Aussage ist kontrafaktisch
 * ("haettest du dieselben kWh oeffentlich geladen"), und eine solche Zahl traegt nur,
 * wenn der Nutzer sie nachrechnen kann.
 */
public record ChargingSavingsResponse(
        BigDecimal homeKwh,
        BigDecimal homePricePerKwh,
        String homePriceBasis,
        BigDecimal publicPricePerKwh,
        String publicPriceBasis,
        int publicPriceSampleSize,
        BigDecimal actuallyPaidEur,
        BigDecimal wouldHaveCostEur,
        BigDecimal savingsEur,
        BigDecimal investmentEur,
        BigDecimal monthsOfUsage,
        Integer firstYear,
        java.util.List<YearlySaving> yearlySavings,
        BigDecimal recoveredEur,
        BigDecimal amortisationYearsRemaining,
        boolean fullyAmortised,
        // Sieht der Nutzer die Kachel nur ueber das Trial (dann Retention-Hinweis), und bis
        // wann laeuft es. trialEndsAt ist null, wenn der Zugang nicht am Trial haengt.
        boolean viaTrial,
        LocalDate trialEndsAt
) {
    public static ChargingSavingsResponse from(ChargingSavings s, boolean viaTrial, LocalDate trialEndsAt) {
        return new ChargingSavingsResponse(
                s.homeKwh(),
                s.homePrice().pricePerKwh(),
                s.homePrice().source().name(),
                s.publicPrice().pricePerKwh(),
                s.publicPrice().source().name(),
                s.publicPrice().sampleSize(),
                s.actuallyPaidEur(),
                s.wouldHaveCostEur(),
                s.savingsEur(),
                s.investmentEur(),
                s.monthsOfUsage(),
                s.firstYear(),
                s.yearlySavings(),
                s.recoveredEur(),
                s.amortisationYearsRemaining(),
                s.fullyAmortised(),
                viaTrial,
                trialEndsAt);
    }
}
