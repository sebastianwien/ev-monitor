package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvLogRepository {
    EvLog save(EvLog evLog);

    Optional<EvLog> findById(UUID id);

    Optional<EvLog> findByIdAndCarId(UUID id, UUID carId);

    List<EvLog> findAll();

    List<EvLog> findAllByCarId(UUID carId);
}
