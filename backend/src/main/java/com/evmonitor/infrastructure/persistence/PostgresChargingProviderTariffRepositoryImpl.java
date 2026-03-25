package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PostgresChargingProviderTariffRepositoryImpl implements ChargingProviderTariffRepository {

    private final JpaChargingProviderTariffRepository jpaRepository;
    private final JpaCpoEmpTierMappingRepository tierMappingRepository;

    public PostgresChargingProviderTariffRepositoryImpl(
            JpaChargingProviderTariffRepository jpaRepository,
            JpaCpoEmpTierMappingRepository tierMappingRepository) {
        this.jpaRepository = jpaRepository;
        this.tierMappingRepository = tierMappingRepository;
    }

    @Override
    public List<ChargingProviderTariff> findAllCurrentTariffs() {
        return jpaRepository.findAllCurrentTariffs().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChargingProviderTariff> findCurrentTariffsByEmp(String empName) {
        return jpaRepository.findCurrentByEmpName(empName).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ChargingProviderTariff> findBestTariff(String empName, String tariffVariant, String cpoName, ChargingType chargingType) {
        String type = chargingType.name();

        // 1. Exakter CPO-Match
        if (cpoName != null) {
            Optional<ChargingProviderTariffEntity> exact = jpaRepository.findByEmpAndVariantAndCpoAndType(empName, tariffVariant, cpoName, type);
            if (exact.isPresent()) return exact.map(this::toDomain);

            // 2. Tier-Match via Mapping
            Optional<String> tier = tierMappingRepository.findTierByEmpAndCpo(empName, cpoName);
            if (tier.isPresent()) {
                Optional<ChargingProviderTariffEntity> tiered = jpaRepository.findByEmpAndVariantAndTierAndType(empName, tariffVariant, tier.get(), type);
                if (tiered.isPresent()) return tiered.map(this::toDomain);
            }

            // Tier-Fallback: STANDARD wenn kein Mapping vorhanden
            Optional<ChargingProviderTariffEntity> standardTier = jpaRepository.findByEmpAndVariantAndTierAndType(empName, tariffVariant, "STANDARD", type);
            if (standardTier.isPresent()) return standardTier.map(this::toDomain);
        }

        // 3a. STANDARD-Tier-Fallback auch wenn kein CPO angegeben (z.B. Maingau ohne CPO-Angabe)
        Optional<ChargingProviderTariffEntity> standardTierFallback = jpaRepository.findByEmpAndVariantAndTierAndType(empName, tariffVariant, "STANDARD", type);
        if (standardTierFallback.isPresent()) return standardTierFallback.map(this::toDomain);

        // 3b. Allgemeiner Fallback (kein CPO, kein Tier) - z.B. IONITY, Fastned
        return jpaRepository.findFallbackByEmpAndVariantAndType(empName, tariffVariant, type).map(this::toDomain);
    }

    @Override
    public List<String> findDistinctTariffVariantsByEmp(String empName) {
        return jpaRepository.findDistinctVariantsByEmp(empName);
    }

    @Override
    public List<String> findAllEmpNames() {
        return jpaRepository.findAllEmpNames();
    }

    @Override
    public Optional<String> findTierForCpo(String empName, String cpoName) {
        return tierMappingRepository.findTierByEmpAndCpo(empName, cpoName);
    }

    private ChargingProviderTariff toDomain(ChargingProviderTariffEntity e) {
        return new ChargingProviderTariff(
                e.getId(),
                e.getEmpName(),
                e.getTariffVariant(),
                e.getCpoName(),
                e.getPriceTier(),
                ChargingType.valueOf(e.getChargingType()),
                e.getPricePerKwh(),
                e.getSessionFeeEur(),
                e.getMonthlyFeeEur(),
                e.getBlockingFeePerMin(),
                e.getBlockingFeeAfterMin(),
                e.isDynamicPricing(),
                e.getValidFrom(),
                e.getValidUntil(),
                e.getSourceUrl(),
                e.getLastVerifiedAt()
        );
    }
}
