package com.evmonitor.application;

import java.util.Optional;

/** Liefert einen sprechenden Ortsnamen (Ortsteil, Dorf, Stadt) fuer die Mitte einer Geohash-Zelle. */
public interface PlaceNameService {

    Optional<String> nameFor(String geohash);
}
