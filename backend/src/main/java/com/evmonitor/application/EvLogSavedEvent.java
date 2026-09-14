package com.evmonitor.application;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wird nach dem Speichern eines EvLogs publiziert und stoesst die Wetter-Anreicherung an.
 *
 * @param lookupAt Zeitpunkt fuer die Wetterabfrage: die Mitte des Ladefensters, nicht der
 *                 Ladebeginn. Eine mehrstuendige AC-Ladung traegt sonst nur die Temperatur
 *                 der ersten Stunde.
 */
public record EvLogSavedEvent(UUID logId, String geohash, LocalDateTime lookupAt, Double temperatureCelsius) {

    public static EvLogSavedEvent of(UUID logId, String geohash, LocalDateTime loggedAt,
                                     Integer chargeDurationMinutes, Double temperatureCelsius) {
        int halfDuration = chargeDurationMinutes != null && chargeDurationMinutes > 0 ? chargeDurationMinutes / 2 : 0;
        return new EvLogSavedEvent(logId, geohash, loggedAt.plusMinutes(halfDuration), temperatureCelsius);
    }
}
