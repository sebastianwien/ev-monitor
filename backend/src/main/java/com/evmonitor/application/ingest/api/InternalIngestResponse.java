package com.evmonitor.application.ingest.api;

import java.util.List;
import java.util.UUID;

/** Antwort auf Vertrag B: je Eintrag in Anfrage-Reihenfolge, ob er angelegt wurde. */
public record InternalIngestResponse(List<EntryResult> chargingSessions, List<EntryResult> trips) {

    public enum Status { CREATED, DUPLICATE }

    /** @param id angelegte Ladung/Fahrt; bei einer doppelten Fahrt die vorhandene, bei einer doppelten Ladung null */
    public record EntryResult(Status status, UUID id) {
        public static EntryResult created(UUID id) { return new EntryResult(Status.CREATED, id); }
        public static EntryResult duplicate(UUID id) { return new EntryResult(Status.DUPLICATE, id); }
    }
}
