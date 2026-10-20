package com.evmonitor.application.routetype;

import java.util.Map;

/**
 * Ergebnis eines Backfill-Laufs. {@code bySource} zählt je "{@code <DATA_SOURCE> -> <ROUTE_TYPE>/<HERKUNFT>}",
 * z. B. "WALLBOX_GOE -> HIGHWAY/TRIPS". Nur Zähler, keine IDs.
 */
public record RouteTypeBackfillSummary(boolean dryRun, int candidates, int changed, int errors, Map<String, Integer> bySource) {}
