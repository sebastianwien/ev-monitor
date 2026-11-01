package com.evmonitor.application.recap;

import com.evmonitor.domain.LeaderboardCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Zwei optionale Zeilen des Monatsrückblicks, dieselben wie in der Rückblick-Karte der Story:
 * die Plätze in der Rangliste des Berichtsmonats (Treppchen alle, sonst der beste bis 10) und der
 * Verbrauch gegen die Fahrer desselben Modells im selben Monat. Leer heißt: Zeile weglassen.
 */
public record RecapHighlights(List<Place> places, PeerConsumption peer) {

    public static final RecapHighlights NONE = new RecapHighlights(List.of(), null);

    public record Place(LeaderboardCategory category, int rank) {}

    /** kWh je 100 km, eigener Wert und Schnitt der anderen Fahrer des Modells. */
    public record PeerConsumption(BigDecimal mine, BigDecimal peers) {}

    public boolean isEmpty() {
        return places.isEmpty() && peer == null;
    }
}
