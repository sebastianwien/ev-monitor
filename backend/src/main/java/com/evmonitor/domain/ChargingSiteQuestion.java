package com.evmonitor.domain;

import java.util.UUID;

/**
 * Frage an den Nutzer, an welcher Register-Säule er an einem Ort mit dieser Ladeart geladen hat.
 *
 * @param geohash        Zelle (7 Stellen) der Ladungen, um die es geht
 * @param chargingType   Ladeart der Ladungen, null wenn unbekannt
 * @param candidatesJson Register-Schnappschuss der Säulen, die in Frage kamen (ohne Position)
 */
public record ChargingSiteQuestion(UUID id, UUID userId, String geohash, ChargingType chargingType,
                                   String candidatesJson, Status status) {

    public enum Status { OPEN, ANSWERED, DISMISSED }

    /** Eine offene Frage mit den Ladungen, die noch auf ihre Antwort warten. */
    public record WithLogs(ChargingSiteQuestion question, int logCount, java.time.LocalDateTime lastLoggedAt) {
    }
}
