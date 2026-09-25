package com.evmonitor.application.ingest;

import com.evmonitor.domain.DataSource;

import java.util.List;
import java.util.UUID;

/** Ladungen eines Autos aus einer Quelle, angenommen über eine Tür. */
public record IngestCommand(UUID userId, UUID carId, DataSource dataSource, IngestDoor door,
                            List<ChargingEntry> entries) {
}
