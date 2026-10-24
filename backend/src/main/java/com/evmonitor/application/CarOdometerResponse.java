package com.evmonitor.application;

/** Aktueller Kilometerstand eines Autos; null, wenn es keinen Tachostand gibt. */
public record CarOdometerResponse(Integer currentOdometerKm) {
}
