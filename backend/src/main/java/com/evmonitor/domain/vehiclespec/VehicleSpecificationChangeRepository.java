package com.evmonitor.domain.vehiclespec;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleSpecificationChangeRepository extends JpaRepository<VehicleSpecificationChange, UUID> {

    List<VehicleSpecificationChange> findAllBySpecIdOrderByChangedAtDesc(UUID specId);
}
