package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.ExtractedTariff.Scope;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.CpoEmpTierMappingEntity;
import com.evmonitor.infrastructure.persistence.JpaCpoEmpTierMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Schreibt, was der Crawler oder der Admin übernimmt, in die Tariftabelle. Eine Preisänderung
 * beendet die alte Zeile am Vortag und legt eine neue ab heute an, damit vergangene Ladungen
 * ihren alten Preis behalten. Jede Änderung verwirft den Cache der Referenzpreise.
 */
@Service
@RequiredArgsConstructor
public class TariffChangeApplier {

    private final ChargingProviderTariffRepository tariffs;
    private final JpaCpoEmpTierMappingRepository tierMapping;

    /** Seite gelesen, Werte stimmen: nur der Stand wird {@code now}. */
    @Transactional
    public void touch(List<ChargingProviderTariff> rows, LocalDateTime now) {
        rows.forEach(r -> tariffs.save(r.toBuilder().lastVerifiedAt(now).build()));
    }

    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public ChargingProviderTariff change(ChargingProviderTariff current, ExtractedTariff found, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        ChargingProviderTariff.ChargingProviderTariffBuilder next = current.toBuilder()
                .pricePerKwh(found.pricePerKwh())
                .lastVerifiedAt(now);
        if (found.monthlyFeeEur() != null) next.monthlyFeeEur(found.monthlyFeeEur());
        if (found.sessionFeeEur() != null) next.sessionFeeEur(found.sessionFeeEur());
        if (found.blockingFeePerMin() != null) next.blockingFeePerMin(found.blockingFeePerMin());
        if (found.blockingFeeAfterMin() != null) next.blockingFeeAfterMin(found.blockingFeeAfterMin());
        if (found.blockingFeeCapEur() != null) next.blockingFeeCapEur(found.blockingFeeCapEur());
        if (found.dynamic()) next.dynamicPricing(true);
        if (!current.getValidFrom().isBefore(today)) return tariffs.save(next.build());
        tariffs.save(current.toBuilder().validUntil(today.minusDays(1)).build());
        return tariffs.save(next.id(UUID.randomUUID()).validFrom(today).validUntil(null).build());
    }

    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public ChargingProviderTariff create(String empName, String sourceUrl, ExtractedTariff f, LocalDateTime now) {
        Scope scope = f.scope() == null ? Scope.OWN_NETWORK : f.scope();
        return tariffs.save(ChargingProviderTariff.builder()
                .id(UUID.randomUUID())
                .empName(empName)
                .tariffVariant(f.variant())
                .cpoName(scope == Scope.OPERATOR ? f.operator() : null)
                .priceTier(scope == Scope.TIER ? f.tier() : null)
                .appliesElsewhere(scope == Scope.ELSEWHERE)
                .chargingType(ChargingType.valueOf(f.chargingType()))
                .minPowerKw(f.minPowerKw())
                .pricePerKwh(f.pricePerKwh())
                .monthlyFeeEur(orZero(f.monthlyFeeEur()))
                .sessionFeeEur(orZero(f.sessionFeeEur()))
                .blockingFeePerMin(f.blockingFeePerMin())
                .blockingFeeAfterMin(f.blockingFeeAfterMin())
                .blockingFeeCapEur(f.blockingFeeCapEur())
                .dynamicPricing(f.dynamic())
                .validFrom(now.toLocalDate())
                .sourceUrl(sourceUrl)
                .lastVerifiedAt(now)
                .build());
    }

    /** Steht nicht mehr auf der Seite: gilt bis gestern (oder nur am ersten Tag, wenn er heute begann). */
    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public void end(ChargingProviderTariff row, LocalDateTime now) {
        LocalDate last = now.toLocalDate().minusDays(1);
        tariffs.save(row.toBuilder().validUntil(last.isBefore(row.getValidFrom()) ? row.getValidFrom() : last).build());
    }

    /** Betreiber der Preisstufe eines Anbieters zuordnen (Maingau-Listen). */
    @Transactional
    public void assignTier(String empName, String cpoName, String tier) {
        CpoEmpTierMappingEntity e = new CpoEmpTierMappingEntity();
        e.setEmpName(empName);
        e.setCpoName(cpoName);
        e.setPriceTier(tier);
        tierMapping.save(e);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
