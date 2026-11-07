package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Antwort der Säulenkarte: je angefragter Zelle die Standorte mit Katalogpreis.
 *
 * @param missing Zellen ohne Antwort (Register antwortet nicht oder Drossel); die Karte fragt sie später erneut
 */
public record ChargingStationMap(List<Cell> cells, List<String> missing) {

    /**
     * @param inGermany ob die Mitte der Zelle in Deutschland liegt; das Register kennt nur deutsche Säulen
     */
    public record Cell(String cell, boolean inGermany, List<Station> stations) {}

    /**
     * Ein Standort auf der Karte.
     *
     * @param latitude Position laut Register, nur zur Anzeige, wird nie gespeichert
     * @param priceAc  günstigster Katalogpreis für Normalladen ohne Grundgebühr, null ohne AC oder ohne Tarif
     * @param priceDc  wie {@code priceAc} für Schnellladen
     */
    public record Station(String id, String name, boolean known, double latitude, double longitude,
                          Double maxAcKw, Double maxDcKw, int chargePoints, List<String> plugTypes,
                          String address, String openingHours, String payment,
                          Price priceAc, Price priceDc) {}

    /** Preis je kWh mit der Karte, die ihn bietet. */
    public record Price(BigDecimal pricePerKwh, String cardName, String variant) {}
}
