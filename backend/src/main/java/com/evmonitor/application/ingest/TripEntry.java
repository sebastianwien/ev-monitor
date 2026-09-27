package com.evmonitor.application.ingest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Eine hochgeladene Fahrt, schon geprüft vom Adapter (Pflichtfelder, Bereiche, Strecke). */
public record TripEntry(OffsetDateTime startedAt, OffsetDateTime endedAt, BigDecimal distanceKm,
                        BigDecimal odometerStartKm, BigDecimal odometerEndKm,
                        BigDecimal socStart, BigDecimal socEnd, String routeType) {
}
