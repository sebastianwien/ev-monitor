package com.evmonitor.application.voice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Kontext fuer die Extraktion, geht als JSON in den Prompt. Schluessel wie im Eval vom 03.10.2026,
 * mit denen Prompt v3 gemessen wurde.
 *
 * @param today             "2026-10-03 (Samstag), Zeitzone Europe/Berlin"
 * @param vehicle           "Nutzbare Akkukapazitaet 77 kWh" aus der SoH-adjustierten Kapazitaet
 * @param lastOdometerKm    letzter bekannter Tachostand, null ohne Logs
 */
public record ExtractionContext(
        @JsonProperty("heute") String today,
        @JsonProperty("fahrzeug") String vehicle,
        @JsonProperty("letzterTachostand") Integer lastOdometerKm,
        @JsonProperty("ortKandidaten") List<Candidate> candidates,
        @JsonProperty("tarife") List<Tariff> tariffs) {

    /** @param kind home, station oder site */
    public record Candidate(int index, String kind, String name) {}

    public record Tariff(int index, String name) {}
}
