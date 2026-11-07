package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Ein Ladestandort in der Naehe, als Vorschlag im Log-Formular: ein Betreiber, zusammengefasst
 * ueber seine Ladeeinrichtungen in der Zelle. Leistung je Ladeart ist das Maximum ueber alle,
 * Registerdaten (ID, Adresse, Stecker) stammen von der naechstgelegenen Einrichtung.
 *
 * @param name           kanonischer Ladenetz-Name, sonst der Rohname aus dem Register
 * @param known          ob der Name einem bekannten Ladenetz zugeordnet werden konnte
 * @param distanceMeters Entfernung zum Mittelpunkt der Geohash-Zelle, nicht zum Nutzer
 * @param maxAcKw        hoechste AC-Steckerleistung am Standort, kann fehlen
 * @param maxDcKw        hoechste DC-Steckerleistung am Standort, kann fehlen
 * @param chargePoints   Ladepunkte am Standort insgesamt
 * @param geohash        Zelle (7 Stellen) der naechstgelegenen Saeule dieses Betreibers
 * @param latitude       Position der naechsten Saeule laut Register - nur zur Anzeige, wird nie gespeichert
 * @param registerId     Ladeeinrichtungs-ID der Bundesnetzagentur (naechste Saeule), kann fehlen
 * @param street         Adresse der naechsten Saeule, Felder koennen fehlen
 */
public record NearbyStation(String name, boolean known, int distanceMeters,
                            Double maxAcKw, Double maxDcKw, int chargePoints, String geohash,
                            Double latitude, Double longitude,
                            Integer registerId, String street, String houseNumber, String postalCode, String city,
                            List<String> plugTypes,
                            LocalDate commissionedOn, String siteLabel, String payment, String openingHours) {

    /** Kompaktform ohne Registerdetails - fuer Tests und Altaufrufer. */
    public NearbyStation(String name, boolean known, int distanceMeters, Double maxPowerKw,
                         boolean fastCharging, int chargePoints, String geohash) {
        this(name, known, distanceMeters, fastCharging ? null : maxPowerKw, fastCharging ? maxPowerKw : null,
                chargePoints, geohash, null, null, null, null, null, null, null, List.of(), null, null, null, null);
    }

    public boolean fastCharging() {
        return maxDcKw != null;
    }

    /** "Strasse Nr, PLZ Ort" fuer die Anzeige, null ohne Adresse. */
    @JsonProperty
    public String address() {
        return RegistrySite.address(street, houseNumber, postalCode, city);
    }

    /** Hoechste Leistung unabhaengig von der Ladeart. */
    public Double maxPowerKw() {
        if (maxDcKw == null) return maxAcKw;
        if (maxAcKw == null) return maxDcKw;
        return Math.max(maxAcKw, maxDcKw);
    }
}
