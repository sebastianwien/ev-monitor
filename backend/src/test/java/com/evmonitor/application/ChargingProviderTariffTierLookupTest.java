package com.evmonitor.application;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingProviderTariffEntity;
import com.evmonitor.infrastructure.persistence.CpoEmpTierMappingEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingProviderTariffRepository;
import com.evmonitor.infrastructure.persistence.JpaCpoEmpTierMappingRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the 3-step tier lookup logic for Maingau-style CPO-dependent pricing.
 *
 * Lookup order:
 * 1. Exact CPO match (emp + cpo + type)
 * 2. Tier match via cpo_emp_tier_mapping (emp + tier + type)
 * 3. Fallback (emp, no cpo, no tier)
 */
class ChargingProviderTariffTierLookupTest extends AbstractIntegrationTest {

    @Autowired
    private ChargingProviderTariffRepository tariffRepository;

    @Autowired
    private JpaChargingProviderTariffRepository jpaTariffRepository;

    @Autowired
    private JpaCpoEmpTierMappingRepository jpaTierMappingRepository;

    @BeforeEach
    void setUp() {
        jpaTierMappingRepository.deleteAll();
        jpaTariffRepository.deleteAll();

        // Maingau tier-based tariffs
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "DC", "LOW",      new BigDecimal("0.6200")));
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "DC", "STANDARD", new BigDecimal("0.7200")));
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "DC", "HIGH",     new BigDecimal("0.8200")));
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "AC", "LOW",      new BigDecimal("0.6200")));
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "AC", "STANDARD", new BigDecimal("0.6200")));
        jpaTariffRepository.save(buildTieredTariff("Maingau", "Standard", "AC", "HIGH",     new BigDecimal("0.6200")));

        // IONITY - no CPO variation, simple fallback tariffs
        jpaTariffRepository.save(buildSimpleTariff("IONITY", "Go",     "DC", new BigDecimal("0.3900"), BigDecimal.ZERO));
        jpaTariffRepository.save(buildSimpleTariff("IONITY", "Direct", "DC", new BigDecimal("0.6900"), BigDecimal.ZERO));
        jpaTariffRepository.save(buildSimpleTariff("IONITY", "Power",  "DC", new BigDecimal("0.3900"), new BigDecimal("11.99")));

        // Tier mappings for Maingau
        jpaTierMappingRepository.save(buildMapping("Maingau", "IONITY",     "LOW"));
        jpaTierMappingRepository.save(buildMapping("Maingau", "Aral Pulse", "HIGH"));
        jpaTierMappingRepository.save(buildMapping("Maingau", "EnBW",       "HIGH"));
        jpaTierMappingRepository.save(buildMapping("Maingau", "Allego",     "STANDARD"));
    }

    // ── Maingau tier lookup ──────────────────────────────────────────────────

    @Test
    void maingau_IonityCpo_ShouldResolveLowTier() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "Maingau", "Standard", "IONITY", ChargingType.DC);

        assertTrue(result.isPresent(), "Should find a Maingau tariff for IONITY DC");
        assertEquals(0, new BigDecimal("0.6200").compareTo(result.get().getPricePerKwh()),
                "IONITY (LOW tier) should be €0.62/kWh");
        assertEquals("LOW", result.get().getPriceTier());
    }

    @Test
    void maingau_AralCpo_ShouldResolveHighTier() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "Maingau", "Standard", "Aral Pulse", ChargingType.DC);

        assertTrue(result.isPresent(), "Should find a Maingau tariff for Aral Pulse DC");
        assertEquals(0, new BigDecimal("0.8200").compareTo(result.get().getPricePerKwh()),
                "Aral Pulse (HIGH tier) should be €0.82/kWh");
        assertEquals("HIGH", result.get().getPriceTier());
    }

    @Test
    void maingau_UnknownCpo_ShouldFallbackToStandardTier() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "Maingau", "Standard", "SomeLocalCharger", ChargingType.DC);

        assertTrue(result.isPresent(), "Should find a fallback Maingau tariff for unknown CPO");
        assertEquals("STANDARD", result.get().getPriceTier(), "Unknown CPO should fall back to STANDARD tier");
        assertEquals(0, new BigDecimal("0.7200").compareTo(result.get().getPricePerKwh()));
    }

    @Test
    void maingau_NullCpo_ShouldFallbackToStandardTier() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "Maingau", "Standard", null, ChargingType.DC);

        assertTrue(result.isPresent(), "Should find a fallback Maingau tariff when no CPO given");
        assertEquals("STANDARD", result.get().getPriceTier());
    }

    // ── IONITY simple lookup ─────────────────────────────────────────────────

    @Test
    void ionity_PowerTariff_ShouldReturnCorrectDcPrice() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "IONITY", "Power", null, ChargingType.DC);

        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("0.3900").compareTo(result.get().getPricePerKwh()));
        assertEquals(0, new BigDecimal("11.99").compareTo(result.get().getMonthlyFeeEur()));
    }

    @Test
    void ionity_DirectTariff_ShouldBeExpensive() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "IONITY", "Direct", null, ChargingType.DC);

        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("0.6900").compareTo(result.get().getPricePerKwh()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get().getMonthlyFeeEur()));
    }

    // ── Tier mapping query ───────────────────────────────────────────────────

    @Test
    void tierMapping_ShouldReturnCorrectTierForKnownCpo() {
        Optional<String> tier = tariffRepository.findTierForCpo("Maingau", "EnBW");
        assertTrue(tier.isPresent());
        assertEquals("HIGH", tier.get());
    }

    @Test
    void tierMapping_ShouldReturnEmpty_ForUnknownCpo() {
        Optional<String> tier = tariffRepository.findTierForCpo("Maingau", "NonExistentCPO");
        assertTrue(tier.isEmpty());
    }

    // ── Non-existent EMP ─────────────────────────────────────────────────────

    @Test
    void unknownEmp_ShouldReturnEmpty() {
        Optional<ChargingProviderTariff> result = tariffRepository.findBestTariff(
                "NonExistentEMP", "Standard", "IONITY", ChargingType.DC);

        assertTrue(result.isEmpty(), "Unknown EMP should return empty");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChargingProviderTariffEntity buildTieredTariff(String emp, String variant, String type, String tier, BigDecimal price) {
        ChargingProviderTariffEntity e = new ChargingProviderTariffEntity();
        e.setId(UUID.randomUUID());
        e.setEmpName(emp);
        e.setTariffVariant(variant);
        e.setChargingType(type);
        e.setPriceTier(tier);
        e.setPricePerKwh(price);
        e.setMonthlyFeeEur(BigDecimal.ZERO);
        e.setSessionFeeEur(BigDecimal.ZERO);
        e.setDynamicPricing(false);
        e.setValidFrom(LocalDate.of(2025, 1, 1));
        e.setValidUntil(null);
        e.setLastVerifiedAt(LocalDateTime.now());
        return e;
    }

    private ChargingProviderTariffEntity buildSimpleTariff(String emp, String variant, String type,
                                                            BigDecimal price, BigDecimal monthlyFee) {
        ChargingProviderTariffEntity e = new ChargingProviderTariffEntity();
        e.setId(UUID.randomUUID());
        e.setEmpName(emp);
        e.setTariffVariant(variant);
        e.setChargingType(type);
        e.setPriceTier(null);
        e.setCpoName(null);
        e.setPricePerKwh(price);
        e.setMonthlyFeeEur(monthlyFee);
        e.setSessionFeeEur(BigDecimal.ZERO);
        e.setDynamicPricing(false);
        e.setValidFrom(LocalDate.of(2025, 1, 1));
        e.setValidUntil(null);
        e.setLastVerifiedAt(LocalDateTime.now());
        return e;
    }

    private CpoEmpTierMappingEntity buildMapping(String emp, String cpo, String tier) {
        CpoEmpTierMappingEntity e = new CpoEmpTierMappingEntity();
        e.setEmpName(emp);
        e.setCpoName(cpo);
        e.setPriceTier(tier);
        return e;
    }
}
