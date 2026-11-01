package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Verbrauch, mit dem die Reichweite rechnet, und woher er kommt (Stufe der
 * {@link RangeConsumptionPolicy}). Das Frontend wählt nach {@code source} und {@code scope} nur den
 * Herkunftssatz. Auf den öffentlichen Seiten ist es zugleich der angezeigte Verbrauch des Modells.
 *
 * @param scope bei {@link Source#PEER_DRIVE} Variante oder Modell, sonst null
 * @param users Fahrer hinter einem {@link Source#PEER_DRIVE}-Wert, sonst 0
 */
public record RangeBasis(BigDecimal consumptionKwhPer100km, Source source, PeerDrive.Scope scope, int users) {

    public enum Source {
        /** Stufe 1: eigene Fahrten ab 20 km im Zeitraum. */
        OWN_DRIVE,
        /** Stufe 2: Fahrten anderer Fahrer derselben Variante oder desselben Modells. */
        PEER_DRIVE,
        /** Stufe 3: eigener Verbrauch aus Ladungen, enthält den Stand. */
        OWN_CHARGES,
        /** Stufe 4: Ladungen der Fahrer des Modells, gemessen, mit Stand. */
        PEER_CHARGES,
        /** Stufe 4 ohne jede Ladung: WLTP mal Flottenfaktor, geschätzt. */
        WLTP_ESTIMATE;

        /** Messwert oder Schätzung: nur Messwerte dürfen Ranglisten sortieren. */
        public boolean isMeasured() {
            return this != WLTP_ESTIMATE;
        }
    }
}
