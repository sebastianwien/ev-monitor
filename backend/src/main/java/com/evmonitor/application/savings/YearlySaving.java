package com.evmonitor.application.savings;

import java.math.BigDecimal;

/** Ersparnis eines Jahres samt aufgelaufener Summe - Stuetzpunkt der Amortisationsschiene. */
public record YearlySaving(
        int year,
        BigDecimal homeKwh,
        BigDecimal paidEur,
        BigDecimal wouldHaveCostEur,
        BigDecimal savingsEur,
        BigDecimal cumulativeEur
) {}
