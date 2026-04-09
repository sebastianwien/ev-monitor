package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaChargingNetworkRepository extends JpaRepository<ChargingNetworkEntity, String> {

    @Query("SELECT n.name FROM ChargingNetworkEntity n ORDER BY n.name")
    List<String> findAllNamesSorted();

    @Query("SELECT n.name FROM ChargingNetworkEntity n WHERE n.countryCode IS NULL OR n.countryCode = :country ORDER BY n.name")
    List<String> findNamesByCountry(@Param("country") String country);
}
