package com.evmonitor.domain;

/**
 * Herkunft des Streckentyps eines Ladevorgangs. Die Rangfolge entscheidet, ob eine Ableitung einen
 * vorhandenen Wert ersetzen darf: eine niedrigere Stufe überschreibt nie eine höhere.
 */
public enum RouteTypeSource {
    /** Im Formular oder Wizard vom Nutzer gewählt. */
    USER(4, true),
    /** Von der Datenquelle mitgeliefert (Spritmonitor-Notiz, API-Upload mit Angabe, Import-Datei). */
    SOURCE(3, true),
    /** Aus den Fahrten desselben Autos zwischen zwei Ladungen abgeleitet. */
    TRIPS(2, true),
    /** Faustregel ohne Fahrten (mittlere Leistung aus SoC-Hub). Zählt nicht in Verbrauchsstatistiken je Streckentyp. */
    HEURISTIC(1, false);

    private final int rank;
    private final boolean countsInStatistics;

    RouteTypeSource(int rank, boolean countsInStatistics) {
        this.rank = rank;
        this.countsInStatistics = countsInStatistics;
    }

    public int rank() {
        return rank;
    }

    public boolean countsInStatistics() {
        return countsInStatistics;
    }
}
