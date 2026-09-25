package com.evmonitor.domain;

/**
 * Auf welchem Weg eine Ladung ankam. Abgeleitet aus {@link DataSource#channel()}, nicht gespeichert.
 */
public enum DataChannel {
    /** Im Formular erfasst. */
    MANUAL,
    /** Vom Nutzer angestoßener Einmal-Import: Datei oder Abruf bei einem Fremddienst. */
    UPLOAD,
    /** Wir holen regelmäßig ab (Scheduler). */
    SYNC,
    /** Hersteller oder Wallbox schickt von sich aus (Webhook, Telemetrie, OCPP). */
    LIVE
}
