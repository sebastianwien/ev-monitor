package com.evmonitor.application;

/**
 * Gemeinsame Regeln für Ladestandorte aus dem Ladesäulenregister, für Formular-Vorschläge
 * ({@link NearbyCpoService}) und Karte ({@link ChargingStationCellService}).
 */
final class RegistrySite {

    private static final double EARTH_RADIUS_M = 6_371_000;

    private RegistrySite() {}

    /**
     * Im Register stehen auch Privatpersonen mit einer Wallbox an der Straße. Ein unbekannter
     * Betreiber mit einem einzigen Normal-Ladepunkt ist kein Ladeort für andere - und sein
     * Name gehört weder ins Formular noch auf die Karte.
     */
    static boolean isChargingSite(boolean known, boolean fastCharging, int chargePoints) {
        return known || fastCharging || chargePoints > 1;
    }

    /** Höhere von zwei Leistungen, null nur wenn beide fehlen. */
    static Double max(Double a, Double b) {
        // Kein verschachtelter Ternär: der würde den Double zu double entpacken und bei null knallen.
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    /** "Straße Nr, PLZ Ort" für die Anzeige, null ohne Adresse. */
    static String address(String street, String houseNumber, String postalCode, String city) {
        if (street == null && city == null) return null;
        String l1 = street == null ? null : houseNumber == null ? street : street + " " + houseNumber;
        String l2 = city == null ? null : postalCode == null ? city : postalCode + " " + city;
        return l1 == null ? l2 : l2 == null ? l1 : l1 + ", " + l2;
    }

    /** Haversine - im Umkreis weniger Kilometer mehr als genau genug. */
    static double distanceMeters(double fromLat, double fromLon, double lat, double lon) {
        double dLat = Math.toRadians(lat - fromLat);
        double dLon = Math.toRadians(lon - fromLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
