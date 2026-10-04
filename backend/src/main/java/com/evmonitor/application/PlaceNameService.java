package com.evmonitor.application;

import java.util.Optional;

/** Liefert einen sprechenden Ortsnamen (Ortsteil, Dorf, Stadt) fuer die Mitte einer Geohash-Zelle. */
public interface PlaceNameService {

    /** Name aus dem Cache oder frisch vom Geocoder (kostet rund eine Sekunde Drossel). */
    Optional<String> nameFor(String geohash);

    /** Nur was schon im Cache liegt - ohne Netz, ohne Wartezeit. */
    Optional<String> cachedNameFor(String geohash);
}
