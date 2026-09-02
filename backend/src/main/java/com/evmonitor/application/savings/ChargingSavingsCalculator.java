package com.evmonitor.application.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rechnet die Heimlade-Ersparnis aus der Jahresreihe.
 *
 * Bewusst ueber die gesamte Zeit, in der daheim geladen wurde, und nicht ueber ein
 * rollierendes Jahr: eine Beschraenkung auf zwoelf Monate wuerde alles davor unsichtbar
 * machen, obwohl es gerechnet ist.
 *
 * Nichts wird geklemmt oder geschoent: laedt jemand daheim teurer als oeffentlich, steht
 * dort eine negative Ersparnis.
 */
public final class ChargingSavingsCalculator {

    private static final int CENTS = 2;
    private static final int PRICE_SCALE = 4;
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private ChargingSavingsCalculator() {}

    /**
     * @param yearly          Ersparnis je Jahr aus den tatsaechlichen Logs
     * @param homePrice       nur fuer die Herkunftsangabe - der angezeigte Preis wird aus
     *                        den Summen gebildet und ist ein Mischwert ueber die Zeit
     * @param investmentEur   Wallbox samt Installation, optional
     * @param monthsOfUsage   bisherige Laufzeit in Monaten. Nenner der Restlaufzeit: die
     *                        Gesamtersparnis geteilt durch die Monate ergibt die Rate.
     *                        Breiter abgestuetzt als ein einzelnes Jahr und unempfindlich
     *                        gegen einen schwachen Winter.
     * @return null, wenn kein Jahr vergleichbar ist - dann zeigt die Kachel ihren
     *         Leerzustand statt einer geratenen Zahl
     */
    public static ChargingSavings calculate(List<YearlySaving> yearly,
                                            PriceBasis homePrice,
                                            PriceBasis publicPrice,
                                            BigDecimal investmentEur,
                                            BigDecimal monthsOfUsage) {
        if (yearly == null || yearly.isEmpty()) return null;

        BigDecimal kwh = sum(yearly, YearlySaving::homeKwh);
        BigDecimal paid = sum(yearly, YearlySaving::paidEur).setScale(CENTS, RoundingMode.HALF_UP);
        BigDecimal would = sum(yearly, YearlySaving::wouldHaveCostEur).setScale(CENTS, RoundingMode.HALF_UP);
        BigDecimal savings = would.subtract(paid);
        BigDecimal recovered = yearly.get(yearly.size() - 1).cumulativeEur();

        // Angezeigte Preise sind Mischwerte: Summe durch Summe. Innerhalb der Rechnung hat
        // jedes Jahr sein eigenes Preisniveau behalten - hier geht es nur um die Anzeige.
        BigDecimal blendedHome = perKwh(paid, kwh);
        BigDecimal blendedPublic = perKwh(would, kwh);

        BigDecimal yearsRemaining = null;
        boolean amortised = false;
        if (investmentEur != null && savings.signum() > 0
                && monthsOfUsage != null && monthsOfUsage.signum() > 0) {
            BigDecimal perMonth = savings.divide(monthsOfUsage, PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal open = investmentEur.subtract(recovered);
            amortised = open.signum() <= 0;
            yearsRemaining = amortised
                    ? BigDecimal.ZERO
                    : open.divide(perMonth, PRICE_SCALE, RoundingMode.HALF_UP)
                          .divide(MONTHS_PER_YEAR, 1, RoundingMode.HALF_UP);
        }

        return new ChargingSavings(
                kwh,
                new PriceBasis(homePrice.source(), blendedHome, homePrice.sampleSize()),
                new PriceBasis(publicPrice.source(), blendedPublic, publicPrice.sampleSize()),
                paid, would, savings, investmentEur, monthsOfUsage, yearly, recovered,
                yearsRemaining, amortised);
    }

    private static BigDecimal sum(List<YearlySaving> years,
                                  java.util.function.Function<YearlySaving, BigDecimal> field) {
        return years.stream().map(field).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal perKwh(BigDecimal total, BigDecimal kwh) {
        return kwh == null || kwh.signum() == 0
                ? BigDecimal.ZERO
                : total.divide(kwh, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
