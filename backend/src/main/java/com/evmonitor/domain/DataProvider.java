package com.evmonitor.domain;

/**
 * Wer die Daten einer Ladung geliefert hat: Hersteller, Aggregator oder Werkzeug. Abgeleitet aus
 * {@link DataSource#provider()}, nicht gespeichert.
 */
public enum DataProvider {
    MANUAL,
    SPRITMONITOR,
    TESLA,
    OCPP_WALLBOX,
    GOE,
    PUBLIC_API,
    TRONITY,
    SMARTCAR,
    VW_GROUP,
    TESSIE,
    XPENG
}
