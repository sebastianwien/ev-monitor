package com.evmonitor.application.ingest;

import com.evmonitor.domain.DataSource;

import java.util.List;
import java.util.UUID;

/** Fahrten eines Autos aus einem Upload (Public API, CSV), angenommen über die Tür {@link IngestDoor#IMPORT_BATCH}. */
public record TripUploadCommand(UUID userId, UUID carId, DataSource dataSource, List<TripEntry> entries) {
}
