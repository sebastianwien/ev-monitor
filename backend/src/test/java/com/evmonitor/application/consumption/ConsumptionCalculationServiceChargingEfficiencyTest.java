package com.evmonitor.application.consumption;

import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Ladeverlust-Faktor nach Ladeleistung: AC unter 4 kW 0,87, ab 4 kW 0,95, ohne Leistung 0,90; DC immer 0,95.
 * Werte aus den Brutto/Netto-Paaren der Plattform (analyse-ladewirkungsgrad.md, Abschnitte 7 und 8).
 */
class ConsumptionCalculationServiceChargingEfficiencyTest {

    private ConsumptionCalculationService service;

    @BeforeEach
    void setUp() {
        PlausibilityProperties props = new PlausibilityProperties();
        props.setAcChargingEfficiency(0.95);
        props.setAcChargingEfficiencySlow(0.87);
        props.setAcChargingEfficiencyUnknownPower(0.90);
        props.setAcSlowChargingThresholdKw(4.0);
        props.setDcChargingEfficiency(0.95);
        service = new ConsumptionCalculationService(
                mock(VehicleSpecificationRepository.class), props, mock(BatterySohRepository.class));
    }

    @Test
    void ac_below4kw_isSlowClass() {
        assertEquals(0.87, service.chargingEfficiency(log(ChargingType.AC, null, "2", null)), 1e-9);
    }

    @Test
    void ac_11kw_isStandardClass() {
        assertEquals(0.95, service.chargingEfficiency(log(ChargingType.AC, null, "11", null)), 1e-9);
    }

    @Test
    void ac_exactly4kw_belongsToStandardClass() {
        assertEquals(0.95, service.chargingEfficiency(log(ChargingType.AC, null, "4.0", null)), 1e-9);
    }

    @Test
    void ac_onlyDuration_lowAveragePower_isSlowClass() {
        // 3 kWh in 2 h = 1,5 kW
        assertEquals(0.87, service.chargingEfficiency(log(ChargingType.AC, null, null, 120, "3")), 1e-9);
    }

    @Test
    void ac_onlyDuration_highAveragePower_isStandardClass() {
        // 20 kWh in 2 h = 10 kW
        assertEquals(0.95, service.chargingEfficiency(log(ChargingType.AC, null, null, 120, "20")), 1e-9);
    }

    @Test
    void ac_withoutPowerAndDuration_keepsUnknownPowerValue() {
        assertEquals(0.90, service.chargingEfficiency(log(ChargingType.AC, null, null, null)), 1e-9);
    }

    @Test
    void dc_ignoresPowerClass() {
        assertEquals(0.95, service.chargingEfficiency(log(ChargingType.DC, null, "2", null)), 1e-9);
    }

    // Ohne Leistung und Dauer bleibt der Ladetyp UNKNOWN (sonst leitet der Konstruktor AC/DC ab).
    @Test
    void unknownType_private_usesAcUnknownPowerValue() {
        assertEquals(0.90, service.chargingEfficiency(log(ChargingType.UNKNOWN, false, null, null)), 1e-9);
    }

    @Test
    void unknownType_public_usesDc() {
        assertEquals(0.95, service.chargingEfficiency(log(ChargingType.UNKNOWN, true, null, null)), 1e-9);
    }

    @Test
    void gridSideKwhEstimate_nettoOnly_dividesBySlowFactor() {
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhAtVehicle(new BigDecimal("10")).costEur(BigDecimal.TEN)
                .maxChargingPowerKw(new BigDecimal("2"))
                .dataSource(DataSource.TESLA_LIVE).chargingType(ChargingType.AC).includeInStatistics(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        assertEquals(0, new BigDecimal("11.4943").compareTo(service.gridSideKwhEstimate(log)));
    }

    private EvLog log(ChargingType type, Boolean publicCharging, String maxPowerKw, Integer durationMinutes) {
        return log(type, publicCharging, maxPowerKw, durationMinutes, "30");
    }

    private EvLog log(ChargingType type, Boolean publicCharging, String maxPowerKw, Integer durationMinutes, String kwh) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID()).carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal(kwh)).costEur(BigDecimal.TEN)
                .maxChargingPowerKw(maxPowerKw != null ? new BigDecimal(maxPowerKw) : null)
                .chargeDurationMinutes(durationMinutes)
                .publicCharging(publicCharging)
                .dataSource(DataSource.USER_LOGGED).chargingType(type).includeInStatistics(true)
                .createdAt(now).updatedAt(now).build();
    }
}
