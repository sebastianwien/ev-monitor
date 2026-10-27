package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresChargingProviderTariffRepositoryImpl implements ChargingProviderTariffRepository {

    private final JpaChargingProviderTariffRepository jpaRepository;
    private final JpaCpoEmpTierMappingRepository tierMappingRepository;
    private final JpaChargingNetworkRepository networkRepository;

    @Override
    public List<ChargingProviderTariff> findAllCurrentTariffs() {
        return jpaRepository.findAllCurrentTariffs().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChargingProviderTariff> findValidOn(LocalDate day) {
        return jpaRepository.findValidOn(day).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ChargingProviderTariff> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ChargingProviderTariff> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public ChargingProviderTariff save(ChargingProviderTariff t) {
        ChargingProviderTariffEntity e = new ChargingProviderTariffEntity();
        e.setId(t.getId() != null ? t.getId() : UUID.randomUUID());
        e.setEmpName(t.getEmpName());
        e.setTariffVariant(t.getTariffVariant());
        e.setCpoName(t.getCpoName());
        e.setPriceTier(t.getPriceTier());
        e.setChargingType(t.getChargingType().name());
        e.setPricePerKwh(t.getPricePerKwh());
        e.setSessionFeeEur(t.getSessionFeeEur());
        e.setMonthlyFeeEur(t.getMonthlyFeeEur());
        e.setBlockingFeePerMin(t.getBlockingFeePerMin());
        e.setBlockingFeeAfterMin(t.getBlockingFeeAfterMin());
        e.setDynamicPricing(t.isDynamicPricing());
        e.setValidFrom(t.getValidFrom());
        e.setValidUntil(t.getValidUntil());
        e.setSourceUrl(t.getSourceUrl());
        e.setLastVerifiedAt(t.getLastVerifiedAt());
        e.setAppliesElsewhere(t.isAppliesElsewhere());
        e.setMinPowerKw(t.getMinPowerKw());
        e.setBlockingFeeCapEur(t.getBlockingFeeCapEur());
        return toDomain(jpaRepository.save(e));
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

    @Override
    public List<String> findAllKnownCpoNames() {
        return networkRepository.findAllNamesSorted();
    }

    @Override
    public List<String> findKnownCpoNamesByCountry(String country) {
        return networkRepository.findNamesByCountry(country);
    }

    private ChargingProviderTariff toDomain(ChargingProviderTariffEntity e) {
        return ChargingProviderTariff.builder()
                .id(e.getId())
                .empName(e.getEmpName())
                .tariffVariant(e.getTariffVariant())
                .cpoName(e.getCpoName())
                .priceTier(e.getPriceTier())
                .chargingType(ChargingType.valueOf(e.getChargingType()))
                .pricePerKwh(e.getPricePerKwh())
                .sessionFeeEur(e.getSessionFeeEur())
                .monthlyFeeEur(e.getMonthlyFeeEur())
                .blockingFeePerMin(e.getBlockingFeePerMin())
                .blockingFeeAfterMin(e.getBlockingFeeAfterMin())
                .dynamicPricing(e.isDynamicPricing())
                .validFrom(e.getValidFrom())
                .validUntil(e.getValidUntil())
                .sourceUrl(e.getSourceUrl())
                .lastVerifiedAt(e.getLastVerifiedAt())
                .appliesElsewhere(e.isAppliesElsewhere())
                .minPowerKw(e.getMinPowerKw())
                .blockingFeeCapEur(e.getBlockingFeeCapEur())
                .build();
    }
}
