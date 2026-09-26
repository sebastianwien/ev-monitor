package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ein Treffer der Textsuche im Ladesaeulenregister, als Kachel im Log-Formular: ein Betreiber
 * an einem Standort (Geohash-Zelle, 7 Stellen), zusammengefasst ueber seine Ladeeinrichtungen
 * dort. Anders als {@link NearbyStation} ohne Entfernung - es gibt keinen Bezugspunkt.
 *
 * @param name         kanonischer Ladenetz-Name, sonst der Rohname aus dem Register
 * @param known        ob der Name einem bekannten Ladenetz zugeordnet werden konnte
 * @param maxAcKw      hoechste AC-Steckerleistung am Standort, kann fehlen
 * @param maxDcKw      hoechste DC-Steckerleistung am Standort, kann fehlen
 * @param chargePoints Ladepunkte am Standort insgesamt
 * @param geohash      Zelle des Standorts - identifiziert ihn zusammen mit dem Namen
 * @param registerId   Ladeeinrichtungs-ID der ersten Saeule dieses Standorts, kann fehlen
 */
public record StationMatch(String name, boolean known, Double maxAcKw, Double maxDcKw, int chargePoints,
                           String geohash, Integer registerId,
                           String street, String houseNumber, String postalCode, String city,
                           List<String> plugTypes) {

    public boolean fastCharging() {
        return maxDcKw != null;
    }

    /** "Strasse Nr, PLZ Ort" fuer die Anzeige, null ohne Adresse. */
    @JsonProperty
    public String address() {
        if (street == null && city == null) return null;
        String l1 = street == null ? null : houseNumber == null ? street : street + " " + houseNumber;
        String l2 = city == null ? null : postalCode == null ? city : postalCode + " " + city;
        return l1 == null ? l2 : l2 == null ? l1 : l1 + ", " + l2;
    }

    /** Im Register stehen auch Privatpersonen mit einer Wallbox - kein Ladeort fuer andere. */
    boolean isChargingSite() {
        return known || fastCharging() || chargePoints > 1;
    }
}
