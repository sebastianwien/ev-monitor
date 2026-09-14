package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ein oeffentlicher Ladestandort: ein Betreiber in einer Geohash-Zelle (7 Stellen, ~150 m).
 * Geteilt zwischen allen Nutzern - die Beziehung zum Nutzer entsteht nur ueber seine Logs.
 *
 * @param cpoName  Name im Ladenetz-Katalog (charging_networks), null wenn der Betreiber dort fehlt
 * @param maxAcKw  hoechste AC-Steckerleistung, null wenn unbekannt
 * @param maxDcKw  hoechste DC-Steckerleistung, null wenn kein DC
 * @param register Daten der naechstgelegenen Ladeeinrichtung aus dem Register, Felder koennen fehlen
 */
public record ChargingSite(UUID id, String name, String cpoName, String geohash,
                           BigDecimal maxAcKw, BigDecimal maxDcKw, int chargePoints,
                           ChargingSiteSource source, RegisterDetails register, LocalDateTime createdAt) {

    public record RegisterDetails(Integer registerId, String street, String houseNumber, String postalCode,
                                  String city, List<String> plugTypes, LocalDate commissionedOn,
                                  String siteLabel, String payment, String openingHours) {
        public static final RegisterDetails NONE =
                new RegisterDetails(null, null, null, null, null, List.of(), null, null, null, null);

        /** "Strasse Nr, PLZ Ort", oder null ohne Adresse. */
        public String address() {
            if (street == null && city == null) return null;
            String l1 = street == null ? null : houseNumber == null ? street : street + " " + houseNumber;
            String l2 = city == null ? null : postalCode == null ? city : postalCode + " " + city;
            return l1 == null ? l2 : l2 == null ? l1 : l1 + ", " + l2;
        }
    }

    public boolean fastCharging() {
        return maxDcKw != null;
    }

    /** Vergleichsschluessel: Gross-/Kleinschreibung und Rand-Leerraum spielen keine Rolle. */
    public static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
