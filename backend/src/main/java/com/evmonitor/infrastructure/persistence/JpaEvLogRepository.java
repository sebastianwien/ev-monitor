package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEvLogRepository extends JpaRepository<EvLogEntity, UUID> {
    Optional<EvLogEntity> findByIdAndCarId(UUID id, UUID carId);

    List<EvLogEntity> findAllByCarId(UUID carId);
}
