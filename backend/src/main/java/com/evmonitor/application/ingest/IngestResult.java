package com.evmonitor.application.ingest;

import com.evmonitor.domain.EvLog;

import java.util.List;

/**
 * Ergebnis eines Gateway-Aufrufs.
 *
 * @param skipped schon vorhanden (auch als Tombstone) oder beim Speichern als Duplikat erkannt
 * @param errors  Einträge, die nur mit {@link IngestPolicy#isolateEntryErrors()} gezählt statt geworfen werden
 * @param created angelegte Ladungen in Verarbeitungsreihenfolge
 */
public record IngestResult(int imported, int skipped, int errors, List<EvLog> created) {
}
