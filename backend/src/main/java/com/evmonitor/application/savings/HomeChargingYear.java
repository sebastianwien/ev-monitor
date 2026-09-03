package com.evmonitor.application.savings;

import java.math.BigDecimal;

/**
 * Ein Kalenderjahr Heimladen, wie es in den Logs steht.
 *
 * @param paidEur                tatsaechlich gezahlt, aus den Logs summiert
 * @param publicPricePerKwh      oeffentliches Preisniveau JENES Jahres. null, wenn es
 *                               sich fuer das Jahr nicht belegen laesst - dann faellt das
 *                               Jahr aus der Rechnung, statt mit einer geratenen Zahl
 *                               mitzulaufen
 */
public record HomeChargingYear(
        int year,
        BigDecimal homeKwh,
        BigDecimal paidEur,
        BigDecimal publicPricePerKwh
) {}
