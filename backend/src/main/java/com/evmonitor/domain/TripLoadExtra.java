package com.evmonitor.domain;

/** Besonderheiten einer Fahrt, die nur der Nutzer kennt. Mehrfachauswahl, gespeichert in {@code ev_trip.load_extras}. */
public enum TripLoadExtra {
    TRAILER,
    /** Dachbox oder Radträger */
    ROOF_BOX,
    FULL_LOAD
}
