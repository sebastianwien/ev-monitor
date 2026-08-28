package com.evmonitor.domain;

/**
 * Kategorie einer Fixkostenposition.
 *
 * <p>Einnahmen-Kategorien ({@link #INCOME}, {@link #COMPENSATION}) tragen intern einen negativen
 * Betrag, damit jeder Verbraucher schlicht aufsummieren kann und die Nettokosten erhaelt.
 * Die Normalisierung passiert in {@link FixedCost#createNew}.
 */
public enum FixedCostCategory {
    INSURANCE,
    TAX,
    TOLL,
    CLEANING,
    MAINTENANCE,
    LEASING,
    FINANCING,
    TIRES,
    TUNING,
    INCOME,
    COMPENSATION,
    OTHER;

    /** True fuer Kategorien, die Geld einbringen statt zu kosten. */
    public boolean isIncome() {
        return this == INCOME || this == COMPENSATION;
    }
}
