package com.evmonitor.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargingSessionGroupRepository {

    ChargingSessionGroup save(ChargingSessionGroup group);

    Optional<ChargingSessionGroup> findById(UUID id);

    List<ChargingSessionGroup> findAllByCarId(UUID carId);

    /**
     * Sucht eine offene Gruppe für ein Fahrzeug:
     * - session_end > threshold (d.h. letzte Sub-Session liegt innerhalb des Merge-Fensters)
     * - Gleicher Kalendertag wie newSessionStart
     * - Gleiche data_source
     */
    Optional<ChargingSessionGroup> findOpenGroupForCar(UUID carId, LocalDateTime threshold,
            LocalDate sessionDay, String dataSource);
}
