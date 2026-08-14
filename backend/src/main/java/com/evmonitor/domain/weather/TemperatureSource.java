package com.evmonitor.domain.weather;

/**
 * Woher die gespeicherte Umgebungstemperatur stammt.
 *
 * <p>Ohne diese Angabe liesse sich nicht entscheiden, ob ein Wert neu geholt werden darf: eine
 * Fahrzeugmessung ist standortgenau und soll bleiben, ein Wetterdienst-Wert darf jederzeit
 * ersetzt werden. {@code null} heisst "Herkunft unbekannt" und ist zugleich der Arbeitsvorrat
 * des Backfill-Jobs.
 */
public enum TemperatureSource {

    /** Vom Fahrzeug gemessen (Telemetrie, Hersteller-Export). Wird nie ueberschrieben. */
    MEASURED,

    /** Von Open-Meteo geholt. Darf ersetzt werden. */
    FORECAST,

    /**
     * Herkunft nicht entscheidbar. Betrifft Altdaten, deren Wert auf einem halben Grad liegt:
     * Fahrzeuge messen in halben Graden, und zwei von zehn Zehnteln des Wetterdienstes sehen
     * genauso aus. Diese Zeilen werden weder korrigiert noch als Messung ausgegeben.
     */
    AMBIGUOUS
}
