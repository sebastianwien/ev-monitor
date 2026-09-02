package com.evmonitor.application.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rechnet die Heimlade-Ersparnis aus geladenen kWh und den beiden Preisen.
 *
 * Reine Rechenlogik. Nichts wird geklemmt oder geschoent: laedt jemand daheim teurer
 * als oeffentlich, steht dort eine negative Ersparnis.
 */
public final class ChargingSavingsCalculator {

    private static final int CENTS = 2;

    private ChargingSavingsCalculator() {}

    /**
     * @param homeKwh        belegte Heimladungen im rollierenden Jahr
     * @param investmentEur  Wallbox samt Installation, optional
     * @param yearly         Ersparnis je Jahr aus den tatsaechlichen Logs. Ihre Summe
     *                       traegt die Amortisation - hochgerechnet wird nichts
     * @return null, wenn einer der beiden Preise unbekannt ist - dann zeigt die Kachel
     *         ihren Leerzustand statt einer geratenen Zahl
     */
    public static ChargingSavings calculate(BigDecimal homeKwh,
                                            PriceBasis homePrice,
                                            PriceBasis publicPrice,
                                            BigDecimal investmentEur,
                                            java.util.List<YearlySaving> yearly) {
        if (homeKwh == null || !homePrice.isKnown() || !publicPrice.isKnown()) return null;

        BigDecimal paid = homeKwh.multiply(homePrice.pricePerKwh()).setScale(CENTS, RoundingMode.HALF_UP);
        BigDecimal would = homeKwh.multiply(publicPrice.pricePerKwh()).setScale(CENTS, RoundingMode.HALF_UP);
        BigDecimal savings = would.subtract(paid);

        java.util.List<YearlySaving> years = yearly != null ? yearly : java.util.List.of();
        BigDecimal recovered = years.isEmpty()
                ? BigDecimal.ZERO.setScale(CENTS)
                : years.get(years.size() - 1).cumulativeEur();

        BigDecimal yearsRemaining = null;
        boolean amortised = false;
        if (investmentEur != null && savings.signum() > 0) {
            BigDecimal open = investmentEur.subtract(recovered);
            amortised = open.signum() <= 0;
            yearsRemaining = amortised
                    ? BigDecimal.ZERO
                    : open.divide(savings, 1, RoundingMode.HALF_UP);
        }

        return new ChargingSavings(homeKwh, homePrice, publicPrice,
                paid, would, savings, investmentEur, years, recovered, yearsRemaining, amortised);
    }
}
