package com.evmonitor.application.ingest.event;

import java.time.Instant;
import java.util.List;

/**
 * Admin-Tab "Importe": Verbindungs-Gesundheit je Hersteller aus Connectors (R2e), nur Aggregate.
 * {@code provider}/{@code channel} benennen die Import-Karte ({@link ImportStatsResponse.Group}), zu der die
 * Verbindungen gehören. {@code available=false}, wenn Connectors nicht antwortet oder den Endpoint noch nicht kennt.
 */
public record ConnectionHealthResponse(boolean available, List<ProviderHealth> providers) {

    static ConnectionHealthResponse unavailable() {
        return new ConnectionHealthResponse(false, List.of());
    }

    /** @param oldestLastSuccessAt ältester letzter Erfolg laufender Verbindungen, null wenn nicht gemessen */
    public record ProviderHealth(String provider, String channel, int total, int active, int failing, int paused,
                                 int inactive, Instant oldestLastSuccessAt, List<ErrorCount> topErrors) {
    }

    /** Fehlerart, von Connectors bereits ohne Inhalte normalisiert. */
    public record ErrorCount(String error, long count) {
    }
}
