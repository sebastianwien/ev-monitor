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
}
