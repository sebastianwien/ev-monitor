package com.evmonitor.application.ingest.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin-Tab "Importe": Aggregate aus {@code import_event}, ohne Nutzer- oder Auto-Bezug.
 * Verbindungsdaten je Provider folgen mit R2e. Zeitpunkte als {@link Instant}, weil {@code created_at}
 * in Serverzeit liegt und der Browser sonst falsch rechnet; Tage ({@code daily}) in Serverzeit.
 */
public record ImportStatsResponse(int days, List<Group> groups, List<DailyCount> daily) {

    /** Ein Provider auf einem Kanal, z. B. VW_GROUP/SYNC. */
    public record Group(String provider, String channel, Health health, long events, double errorRate,
                        long sessionsImported, long sessionsSkipped, long sessionsFailed,
                        long tripsImported, long tripsSkipped,
                        Map<ImportEventOutcome, Long> outcomes,
                        Instant lastEventAt, Instant lastSuccessAt,
                        List<TopError> topErrors) {
    }

    public record TopError(String error, long count, Instant lastAt) {
    }

    public record DailyCount(LocalDate date, ImportEventOutcome outcome, long count) {
    }

    /**
     * Ampel aus der Störungsquote: FAILED und PARSE_ERROR je Aufruf. REJECTED zählt nicht, das ist
     * eine unpassende Anfrage (fremdes Auto, fehlendes Feld), keine Störung beim Hersteller.
     */
    public enum Health {
        OK, WARN, ERROR;

        static final double WARN_FROM = 0.05;
        static final double ERROR_FROM = 0.20;

        public static Health of(long failures, long events) {
            if (events == 0) return OK;
            double rate = (double) failures / events;
            if (rate >= ERROR_FROM) return ERROR;
            return rate >= WARN_FROM ? WARN : OK;
        }
    }
}
