package com.evmonitor.domain.weather;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain-Port für die asynchrone Anreicherung von EV-Logs mit Umgebungstemperatur.
 * Der Adapter in der Infrastructure-Schicht (aktuell
 * {@code com.evmonitor.infrastructure.weather.TemperatureEnrichmentService}) holt die
 * Daten von einer externen Wetter-API und persistiert sie über das EvLogRepository.
 *
 * <p>Diese Trennung erlaubt es dem Application-Layer (EvLogService), ohne direkte
 * Abhängigkeit zur Infrastructure-Schicht zu arbeiten. Das ist der Kern der
 * Dependency-Rule von Clean Architecture: Domain/Application kennt kein HTTP,
 * kein Open-Meteo, keine Geohash-Lib.
 */
public interface TemperatureEnricher {

    /**
     * Reichert den angegebenen Log asynchron mit Temperaturdaten an.
     * Implementierungen dürfen bei null/leerem Geohash früh returnen.
     * Fehler werden geloggt, aber nicht propagiert — die Anreicherung ist best-effort.
     */
    void enrichLog(UUID logId, String geohash, LocalDateTime loggedAt);

    /**
     * Reichert den angegebenen Trip asynchron mit Temperaturdaten an.
     * Wird aufgerufen wenn der Trip ohne Temperatur gespeichert wurde (z.B. SmartCar-Trips).
     */
    default void enrichTrip(UUID tripId, String geohash, LocalDateTime startedAt) {}
}
