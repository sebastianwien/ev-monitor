package com.evmonitor.application.savings;

import java.math.BigDecimal;

/**
 * Ergebnis der Heimlade-Ersparnis fuer das rollierende Jahr.
 *
 * Traegt die Eingangsgroessen mit, damit die Kachel den Rechenweg zeigen kann - bei
 * einer kontrafaktischen Aussage ("haettest du oeffentlich geladen") entsteht Vertrauen
 * nur, wenn sie nachrechenbar ist.
 *
 * @param monthsOfUsage             bisherige Laufzeit in Monaten - Nenner der Restlaufzeit
 * @param yearlySavings             Ersparnis je Kalenderjahr, aus den tatsaechlichen Logs.
 *                                  Traegt die Amortisationsschiene: erstes Jahr ist ihr
 *                                  Beginn, die aufgelaufene Summe ihr Fuellstand
 * @param recoveredEur              Summe der Jahresersparnisse - keine Hochrechnung
 * @param amortisationYearsRemaining null, wenn keine Investition hinterlegt ist oder
 *                                   ohne Ersparnis nichts amortisiert
 */
public record ChargingSavings(
        BigDecimal homeKwh,
        PriceBasis homePrice,
        PriceBasis publicPrice,
        BigDecimal actuallyPaidEur,
        BigDecimal wouldHaveCostEur,
        BigDecimal savingsEur,
        BigDecimal investmentEur,
        BigDecimal monthsOfUsage,
        java.util.List<YearlySaving> yearlySavings,
        BigDecimal recoveredEur,
        BigDecimal amortisationYearsRemaining,
        boolean fullyAmortised
) {

    /** Beginn der Amortisationsschiene - das erste Jahr mit belegtem Heimladen. */
    public Integer firstYear() {
        return yearlySavings == null || yearlySavings.isEmpty() ? null : yearlySavings.get(0).year();
    }
}
