package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaCpoEmpTierMappingRepository extends JpaRepository<CpoEmpTierMappingEntity, CpoEmpTierMappingId> {

    @Query("SELECT m.priceTier FROM CpoEmpTierMappingEntity m WHERE m.empName = :empName AND m.cpoName = :cpoName")
    Optional<String> findTierByEmpAndCpo(@Param("empName") String empName, @Param("cpoName") String cpoName);

    @Query("SELECT DISTINCT m.cpoName FROM CpoEmpTierMappingEntity m ORDER BY m.cpoName")
    List<String> findAllDistinctCpoNames();
}
