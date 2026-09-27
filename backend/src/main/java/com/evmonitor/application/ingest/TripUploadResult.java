package com.evmonitor.application.ingest;

import com.evmonitor.domain.EvTrip;

import java.util.List;

/**
 * @param skipped Start schon belegt (auch durch eine gelöschte Fahrt oder einen früheren Eintrag im Upload)
 * @param created angelegte Fahrten in Reihenfolge der Einträge
 */
public record TripUploadResult(int imported, int skipped, List<EvTrip> created) {
}
