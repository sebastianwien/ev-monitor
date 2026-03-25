package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaChargingProviderTariffRepository extends JpaRepository<ChargingProviderTariffEntity, UUID> {

    @Query("SELECT t FROM ChargingProviderTariffEntity t WHERE t.validUntil IS NULL ORDER BY t.empName, t.tariffVariant")
    List<ChargingProviderTariffEntity> findAllCurrentTariffs();

    @Query("SELECT t FROM ChargingProviderTariffEntity t WHERE t.empName = :empName AND t.validUntil IS NULL")
    List<ChargingProviderTariffEntity> findCurrentByEmpName(@Param("empName") String empName);

    // 1. Exakter CPO-Match
    @Query("SELECT t FROM ChargingProviderTariffEntity t WHERE t.empName = :empName AND t.tariffVariant = :tariffVariant AND t.cpoName = :cpoName AND t.chargingType = :chargingType AND t.validUntil IS NULL")
    Optional<ChargingProviderTariffEntity> findByEmpAndVariantAndCpoAndType(
            @Param("empName") String empName,
            @Param("tariffVariant") String tariffVariant,
            @Param("cpoName") String cpoName,
            @Param("chargingType") String chargingType);

    // 2. Tier-Match
    @Query("SELECT t FROM ChargingProviderTariffEntity t WHERE t.empName = :empName AND t.tariffVariant = :tariffVariant AND t.priceTier = :tier AND t.chargingType = :chargingType AND t.validUntil IS NULL")
    Optional<ChargingProviderTariffEntity> findByEmpAndVariantAndTierAndType(
            @Param("empName") String empName,
            @Param("tariffVariant") String tariffVariant,
            @Param("tier") String tier,
            @Param("chargingType") String chargingType);

    // 3. Fallback (kein CPO, kein Tier)
    @Query("SELECT t FROM ChargingProviderTariffEntity t WHERE t.empName = :empName AND t.tariffVariant = :tariffVariant AND t.cpoName IS NULL AND t.priceTier IS NULL AND t.chargingType = :chargingType AND t.validUntil IS NULL")
    Optional<ChargingProviderTariffEntity> findFallbackByEmpAndVariantAndType(
            @Param("empName") String empName,
            @Param("tariffVariant") String tariffVariant,
            @Param("chargingType") String chargingType);

    @Query("SELECT DISTINCT t.tariffVariant FROM ChargingProviderTariffEntity t WHERE t.empName = :empName AND t.validUntil IS NULL AND t.tariffVariant IS NOT NULL")
    List<String> findDistinctVariantsByEmp(@Param("empName") String empName);

    @Query("SELECT DISTINCT t.empName FROM ChargingProviderTariffEntity t WHERE t.validUntil IS NULL ORDER BY t.empName")
    List<String> findAllEmpNames();
}
