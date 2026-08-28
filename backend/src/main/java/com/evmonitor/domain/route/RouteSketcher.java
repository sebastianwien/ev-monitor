package com.evmonitor.domain.route;

import java.util.UUID;

/**
 * Domain-Port fuer die asynchrone Anreicherung einer Fahrt mit einer gerechneten
 * Strassenverbindung zwischen Start- und Zielgegend.
 *
 * <p>Wichtig fuer alle Aufrufer: Das Ergebnis ist <b>nicht die gefahrene Strecke</b>. Es
 * gibt keine aufgezeichnete Route - gespeichert wird der Weg, den ein Router zwischen den
 * beiden gerundeten Geohash-Mittelpunkten vorschlaegt. Die Laenge dieser Linie weicht
 * deshalb von der gemessenen Distanz der Fahrt ab.
 *
 * <p>Wie beim Temperatur-Port haelt diese Trennung HTTP und den konkreten Router-Anbieter
 * aus Domain und Application heraus; der Adapter liegt in der Infrastructure-Schicht.
 */
public interface RouteSketcher {

    /**
     * Berechnet und persistiert die Verbindung asynchron. Best-effort: fehlende Geohashes,
     * fehlender API-Key oder ein Fehler des Routers fuehren dazu, dass die Fahrt einfach
     * ohne Linie bleibt - die Kachel faellt dann auf die Luftlinie zurueck.
     */
    void sketchTrip(UUID tripId, String startGeohash, String endGeohash);

    /**
     * Legt die gefahrene Spur auf das Strassennetz: der Router verbindet ihre Stuetzpunkte
     * entlang echter Strassen, statt sie geradlinig zu schneiden. Ergebnis ist eine Naeherung
     * der gefahrenen Strecke - naeher dran als die Skizze aus {@link #sketchTrip}, aber
     * zwischen zwei Stuetzpunkten weiterhin geraten.
     *
     * <p>Best-effort wie {@link #sketchTrip}: schlaegt es fehl, bleibt die rohe Spur stehen.
     *
     * <p>Das Ergebnis wird gegen die gemessene Fahrleistung geprueft: der Router faehrt jeden
     * Stuetzpunkt exakt an und baut dort Schleifen, wo einer neben der befahrenen Strasse liegt.
     * Eine Linie, die nicht zur Laenge der Fahrt passt, wird verworfen.
     *
     * @param tracePolyline die gefahrene Spur als encodierte Polyline
     * @param distanceKm    gemessene Fahrleistung als Massstab; ohne sie unterbleibt das Matching
     */
    void matchTrace(UUID tripId, String tracePolyline, java.math.BigDecimal distanceKm);
}
