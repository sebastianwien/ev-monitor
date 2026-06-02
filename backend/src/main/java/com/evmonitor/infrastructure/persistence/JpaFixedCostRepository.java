package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaFixedCostRepository extends JpaRepository<FixedCostEntity, UUID> {
    List<FixedCostEntity> findAllByCarId(UUID carId);
}
