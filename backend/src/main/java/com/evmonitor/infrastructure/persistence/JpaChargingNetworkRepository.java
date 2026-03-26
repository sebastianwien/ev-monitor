package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JpaChargingNetworkRepository extends JpaRepository<ChargingNetworkEntity, String> {

    @Query("SELECT n.name FROM ChargingNetworkEntity n ORDER BY n.name")
    List<String> findAllNamesSorted();
}
