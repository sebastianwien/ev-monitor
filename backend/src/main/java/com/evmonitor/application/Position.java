package com.evmonitor.application;

/** Eine Position des Nutzers, nur zum Vergleich mit den eigenen Logs - wird nie gespeichert. */
public record Position(double lat, double lon) {

    public static boolean onEarth(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }
}
