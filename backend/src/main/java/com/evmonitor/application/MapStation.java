package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ein Ladestandort auf der Karte: ein Betreiber an einem Platz, zusammengefasst über seine
 * Ladeeinrichtungen dort. Nur Registerdaten, keine Preise - die ändern sich mit dem Katalog
 * und werden je Anfrage dazugerechnet.
 *
 * @param id           Ladeeinrichtungs-ID der ersten Einrichtung, ohne ID eine Ersatz-ID aus Platz und Name
 * @param name         kanonischer Ladenetz-Name, sonst der Rohname aus dem Register
 * @param known        ob der Name einem bekannten Ladenetz zugeordnet werden konnte
 * @param latitude     Position der ersten Einrichtung laut Register - nur zur Anzeige, wird nie gespeichert
 * @param maxAcKw      höchste AC-Steckerleistung am Standort, kann fehlen
 * @param maxDcKw      höchste DC-Steckerleistung am Standort, kann fehlen
 * @param chargePoints Ladepunkte am Standort insgesamt
 * @param plugTypes    alle Stecker am Standort, ohne Doppelte
 */
public record MapStation(String id, String name, boolean known, double latitude, double longitude,
                         Double maxAcKw, Double maxDcKw, int chargePoints, List<String> plugTypes,
                         String street, String houseNumber, String postalCode, String city,
                         String openingHours, String payment) {

    @JsonProperty
    public String address() {
        return RegistrySite.address(street, houseNumber, postalCode, city);
    }
}
