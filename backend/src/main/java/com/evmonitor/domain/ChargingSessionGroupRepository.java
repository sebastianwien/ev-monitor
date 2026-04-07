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
     * Löscht alle Session-Gruppen eines Users für eine bestimmte DataSource.
     * Muss NACH dem Löschen der zugehörigen ev_log-Einträge aufgerufen werden
     * (FK-Constraint: ev_log.session_group_id → charging_session_group.id).
     */
    void deleteAllByUserIdAndDataSource(UUID userId, String dataSource);

    /**
     * Sucht eine offene Gruppe für ein Fahrzeug:
     * - session_end > threshold (d.h. letzte Sub-Session liegt innerhalb des Merge-Fensters)
     * - Gleicher Kalendertag wie newSessionStart
     * - Gleiche data_source
     */
    Optional<ChargingSessionGroup> findOpenGroupForCar(UUID carId, LocalDateTime threshold,
            LocalDate sessionDay, String dataSource);

    void updateCarId(UUID groupId, UUID targetCarId);
}
