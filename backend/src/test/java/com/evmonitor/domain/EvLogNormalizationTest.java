package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the AT_VEHICLE normalization invariant in EvLog:
 * when measurement_type=AT_VEHICLE + kwhCharged set + kwhAtVehicle not set,
 * the constructor moves the value to kwhAtVehicle and clears kwhCharged.
 */
class EvLogNormalizationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    void smartcarLive_kwhCharged_movedToKwhAtVehicle() {
        EvLog log = builder().dataSource(DataSource.SMARTCAR_LIVE).kwhCharged(bd("45.0")).build();

        assertNull(log.getKwhCharged());
        assertEquals(bd("45.0"), log.getKwhAtVehicle());
        assertEquals(EnergyMeasurementType.AT_VEHICLE, log.getMeasurementType());
    }

    @Test
    void teslaLive_kwhCharged_movedToKwhAtVehicle() {
        EvLog log = builder().dataSource(DataSource.TESLA_LIVE).kwhCharged(bd("38.5")).build();

        assertNull(log.getKwhCharged());
        assertEquals(bd("38.5"), log.getKwhAtVehicle());
    }

    @Test
    void tessie_kwhCharged_movedToKwhAtVehicle() {
        EvLog log = builder().dataSource(DataSource.TESSIE).kwhCharged(bd("52.0")).build();

        assertNull(log.getKwhCharged());
        assertEquals(bd("52.0"), log.getKwhAtVehicle());
    }

    @Test
    void explicitAtVehicle_kwhCharged_movedToKwhAtVehicle() {
        EvLog log = builder()
                .dataSource(DataSource.API_UPLOAD)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .kwhCharged(bd("30.0"))
                .build();

        assertNull(log.getKwhCharged());
        assertEquals(bd("30.0"), log.getKwhAtVehicle());
    }

    @Test
    void atCharger_kwhCharged_notNormalized() {
        EvLog log = builder().dataSource(DataSource.USER_LOGGED).kwhCharged(bd("50.0")).build();

        assertEquals(bd("50.0"), log.getKwhCharged());
        assertNull(log.getKwhAtVehicle());
        assertEquals(EnergyMeasurementType.AT_CHARGER, log.getMeasurementType());
    }

    @Test
    void bothFieldsSet_notNormalized() {
        EvLog log = builder()
                .dataSource(DataSource.SMARTCAR_LIVE)
                .kwhCharged(bd("50.0"))
                .kwhAtVehicle(bd("47.0"))
                .build();

        assertEquals(bd("50.0"), log.getKwhCharged());
        assertEquals(bd("47.0"), log.getKwhAtVehicle());
    }

    @Test
    void kwhAtVehicleOnly_notNormalized() {
        EvLog log = builder()
                .dataSource(DataSource.USER_LOGGED)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .kwhAtVehicle(bd("44.0"))
                .build();

        assertNull(log.getKwhCharged());
        assertEquals(bd("44.0"), log.getKwhAtVehicle());
    }

    private EvLog.EvLogBuilder builder() {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .includeInStatistics(true)
                .loggedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW);
    }

    private BigDecimal bd(String val) { return new BigDecimal(val); }
}
