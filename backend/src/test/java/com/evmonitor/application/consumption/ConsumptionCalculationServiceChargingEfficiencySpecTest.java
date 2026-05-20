package com.evmonitor.application.consumption;

import com.evmonitor.application.ConsumptionResult;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for spec-level charging efficiency override in ConsumptionCalculationService.
 *
 * Verifies that VehicleSpecification.chargingEfficiencyDc / chargingEfficiencyAc take
 * precedence over the global PlausibilityProperties defaults when present.
 */
@ExtendWith(MockitoExtension.class)
class ConsumptionCalculationServiceChargingEfficiencySpecTest {

    private ConsumptionCalculationService service;

    // Global defaults
    private static final double GLOBAL_DC = 0.95;
    private static final double GLOBAL_AC = 0.90;

    @BeforeEach
    void setUp() {
        PlausibilityProperties props = new PlausibilityProperties();
        props.setDcChargingEfficiency(GLOBAL_DC);
        props.setAcChargingEfficiency(GLOBAL_AC);
        service = new ConsumptionCalculationService(
                mock(VehicleSpecificationRepository.class), props, mock(BatterySohRepository.class));
    }

    // -------------------------------------------------------------------------
    // chargingEfficiency(log, spec) - spec takes precedence
    // -------------------------------------------------------------------------

    /** DC log + spec with chargingEfficiencyDc=0.92 → uses 0.92, not global 0.95 */
    @Test
    void chargingEfficiency_dcLog_specDcEfficiency_usesSpecValue() {
        EvLog log = dcLog(new BigDecimal("40.0"));
        VehicleSpecification spec = specWithEfficiencies(new BigDecimal("0.92"), new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // 40.0 * 0.92 = 36.80
        assertEquals(0, new BigDecimal("36.80").compareTo(effective));
    }

    /** AC log + spec with chargingEfficiencyAc=0.88 → uses 0.88, not global 0.90 */
    @Test
    void chargingEfficiency_acLog_specAcEfficiency_usesSpecValue() {
        EvLog log = acLog(new BigDecimal("40.0"));
        VehicleSpecification spec = specWithEfficiencies(new BigDecimal("0.92"), new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // 40.0 * 0.88 = 35.20
        assertEquals(0, new BigDecimal("35.20").compareTo(effective));
    }

    /** spec=null → falls back to global defaults (DC 0.95) */
    @Test
    void chargingEfficiency_nullSpec_fallsBackToGlobalDc() {
        EvLog log = dcLog(new BigDecimal("40.0"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, null);

        // 40.0 * 0.95 = 38.0
        assertEquals(0, new BigDecimal("38.0").compareTo(effective));
    }

    /** spec=null → falls back to global defaults (AC 0.90) */
    @Test
    void chargingEfficiency_nullSpec_fallsBackToGlobalAc() {
        EvLog log = acLog(new BigDecimal("40.0"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, null);

        // 40.0 * 0.90 = 36.0
        assertEquals(0, new BigDecimal("36.0").compareTo(effective));
    }

    /** DC log + spec where chargingEfficiencyDc=null (only AC is set) → falls back to global DC 0.95 */
    @Test
    void chargingEfficiency_dcLog_specDcNull_fallsBackToGlobal() {
        EvLog log = dcLog(new BigDecimal("40.0"));
        VehicleSpecification spec = specWithEfficiencies(null, new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // 40.0 * 0.95 (global fallback)
        assertEquals(0, new BigDecimal("38.0").compareTo(effective));
    }

    /** UNKNOWN charging_type + isPublic=true + spec with DC efficiency → uses spec DC efficiency as proxy */
    @Test
    void chargingEfficiency_unknownType_publicCharging_specDcEfficiency_usedAsProxy() {
        EvLog log = unknownTypeLog(new BigDecimal("40.0"), true);
        VehicleSpecification spec = specWithEfficiencies(new BigDecimal("0.92"), new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // Public charging → DC proxy → 40.0 * 0.92
        assertEquals(0, new BigDecimal("36.80").compareTo(effective));
    }

    /** UNKNOWN charging_type + isPublic=false + spec with AC efficiency → uses spec AC efficiency as proxy */
    @Test
    void chargingEfficiency_unknownType_privateCharging_specAcEfficiency_usedAsProxy() {
        EvLog log = unknownTypeLog(new BigDecimal("40.0"), false);
        VehicleSpecification spec = specWithEfficiencies(new BigDecimal("0.92"), new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // Private charging → AC proxy → 40.0 * 0.88
        assertEquals(0, new BigDecimal("35.20").compareTo(effective));
    }

    /** UNKNOWN + isPublic=true + spec DC=null → falls back to global DC */
    @Test
    void chargingEfficiency_unknownType_publicCharging_specDcNull_fallsBackToGlobal() {
        EvLog log = unknownTypeLog(new BigDecimal("40.0"), true);
        VehicleSpecification spec = specWithEfficiencies(null, new BigDecimal("0.88"));

        BigDecimal effective = service.effectiveKwhForConsumption(log, spec);

        // DC proxy, but spec DC=null → global DC 0.95 → 40.0 * 0.95
        assertEquals(0, new BigDecimal("38.0").compareTo(effective));
    }

    // -------------------------------------------------------------------------
    // calculateConsumptionPerLog with spec
    // -------------------------------------------------------------------------

    /**
     * End-to-end: two logs, DC charging, spec with DC efficiency 0.92.
     *
     * logX: odometer=10000, socAfter=80%
     * logY: odometer=10300, kwhCharged=52.5 (DC), socAfter=80%
     * battery=75 kWh, distance=300 km
     *
     * effectiveKwh = 52.5 * 0.92 = 48.30
     * socDeltaCorrection = (80 - 80) * 75 / 100 = 0
     * consumption = 48.30 / 300 * 100 = 16.10 kWh/100km
     */
    @Test
    void calculateConsumptionPerLog_withSpec_dcEfficiency_endToEnd() {
        EvLog logX = evLog(UUID.randomUUID(), 10000, null, 80, LocalDateTime.of(2026, 1, 1, 10, 0), ChargingType.UNKNOWN, false);
        EvLog logY = evLog(UUID.randomUUID(), 10300, new BigDecimal("52.5"), 80, LocalDateTime.of(2026, 1, 2, 10, 0), ChargingType.DC, true);
        VehicleSpecification spec = specWithEfficiencies(new BigDecimal("0.92"), new BigDecimal("0.88"));

        Map<UUID, ConsumptionResult> result = service.calculateConsumptionPerLog(
                List.of(logX, logY),
                new BigDecimal("75.0"),
                null,
                spec);

        assertTrue(result.containsKey(logY.getId()), "logY should have a consumption result");
        assertEquals(new BigDecimal("16.10"), result.get(logY.getId()).value());
    }

    /**
     * Without spec: same setup uses global DC 0.95.
     * effectiveKwh = 52.5 * 0.95 = 49.875
     * consumption = 49.875 / 300 * 100 = 16.63 kWh/100km
     */
    @Test
    void calculateConsumptionPerLog_withoutSpec_usesGlobalDcEfficiency() {
        EvLog logX = evLog(UUID.randomUUID(), 10000, null, 80, LocalDateTime.of(2026, 1, 1, 10, 0), ChargingType.UNKNOWN, false);
        EvLog logY = evLog(UUID.randomUUID(), 10300, new BigDecimal("52.5"), 80, LocalDateTime.of(2026, 1, 2, 10, 0), ChargingType.DC, true);

        Map<UUID, ConsumptionResult> result = service.calculateConsumptionPerLog(
                List.of(logX, logY),
                new BigDecimal("75.0"),
                null,
                null);

        assertTrue(result.containsKey(logY.getId()));
        assertEquals(new BigDecimal("16.63"), result.get(logY.getId()).value());
    }

    // -------------------------------------------------------------------------
    // Backward compat: existing overloads without spec still work
    // -------------------------------------------------------------------------

    @Test
    void effectiveKwhForConsumption_withoutSpec_backwardCompat() {
        EvLog log = dcLog(new BigDecimal("40.0"));
        // old overload without spec → should use global DC 0.95
        BigDecimal effective = service.effectiveKwhForConsumption(log);
        assertEquals(0, new BigDecimal("38.0").compareTo(effective));
    }

    @Test
    void calculateConsumptionPerLog_withoutSpec_backwardCompat() {
        EvLog logX = evLog(UUID.randomUUID(), 10000, null, 80, LocalDateTime.of(2026, 1, 1, 10, 0), ChargingType.UNKNOWN, false);
        EvLog logY = evLog(UUID.randomUUID(), 10300, new BigDecimal("52.5"), 80, LocalDateTime.of(2026, 1, 2, 10, 0), ChargingType.DC, true);

        // should not throw and should return a result
        Map<UUID, ConsumptionResult> result = service.calculateConsumptionPerLog(
                List.of(logX, logY),
                new BigDecimal("75.0"),
                null);

        assertFalse(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private VehicleSpecification specWithEfficiencies(BigDecimal dc, BigDecimal ac) {
        return new VehicleSpecification(
                UUID.randomUUID(), "Tesla", "Model 3",
                new BigDecimal("75.0"), new BigDecimal("500"),
                new BigDecimal("14.0"), VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP,
                LocalDateTime.now(), LocalDateTime.now(),
                "", null, null, null,
                dc, ac);
    }

    private EvLog dcLog(BigDecimal kwhCharged) {
        return baseLog(kwhCharged, ChargingType.DC, true);
    }

    private EvLog acLog(BigDecimal kwhCharged) {
        return baseLog(kwhCharged, ChargingType.AC, false);
    }

    private EvLog unknownTypeLog(BigDecimal kwhCharged, boolean isPublic) {
        return baseLog(kwhCharged, ChargingType.UNKNOWN, isPublic);
    }

    private EvLog baseLog(BigDecimal kwhCharged, ChargingType chargingType, boolean isPublic) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .costEur(BigDecimal.TEN)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(chargingType)
                .publicCharging(isPublic)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private EvLog evLog(UUID id, Integer odometerKm, BigDecimal kwhCharged,
                        int socAfterPercent, LocalDateTime loggedAt, ChargingType chargingType, boolean isPublic) {
        return EvLog.builder()
                .id(id)
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .odometerKm(odometerKm)
                .maxChargingPowerKw(new BigDecimal("11.0"))
                .socAfterChargePercent(new BigDecimal(socAfterPercent))
                .loggedAt(loggedAt)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(chargingType)
                .publicCharging(isPublic)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }
}
