package com.evmonitor.application.savings;

import java.math.BigDecimal;

/**
 * Ergebnis der Heimlade-Ersparnis fuer das rollierende Jahr.
 *
 * Traegt die Eingangsgroessen mit, damit die Kachel den Rechenweg zeigen kann - bei
 * einer kontrafaktischen Aussage ("haettest du oeffentlich geladen") entsteht Vertrauen
 * nur, wenn sie nachrechenbar ist.
 *
 * @param recoveredEur              kumulierte Ersparnis ueber die gesamte Nutzungsdauer
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
        BigDecimal recoveredEur,
        BigDecimal amortisationYearsRemaining,
        boolean fullyAmortised
) {}
