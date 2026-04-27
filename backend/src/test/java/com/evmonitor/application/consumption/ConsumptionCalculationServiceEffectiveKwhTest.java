package com.evmonitor.application.consumption;

import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ConsumptionCalculationServiceEffectiveKwhTest {

    private ConsumptionCalculationService service;

    @BeforeEach
    void setUp() {
        PlausibilityProperties props = new PlausibilityProperties();
        props.setAcChargingEfficiency(0.9);
        props.setDcChargingEfficiency(0.95);
        service = new ConsumptionCalculationService(
                mock(VehicleSpecificationRepository.class), props, mock(BatterySohRepository.class));
    }

    @Test
    void effectiveKwhForConsumption_returnsNull_whenBothKwhFieldsNull() {
        EvLog log = log(null, null);
        assertNull(service.effectiveKwhForConsumption(log));
    }

    @Test
    void effectiveKwhForConsumption_usesKwhAtVehicle_whenPresent() {
        EvLog log = log(new BigDecimal("40.0"), null);
        assertEquals(0, new BigDecimal("40.0").compareTo(service.effectiveKwhForConsumption(log)));
    }

    @Test
    void effectiveKwhForConsumption_appliesEfficiency_whenOnlyKwhChargedPresent() {
        EvLog log = log(null, new BigDecimal("40.0"));
        // AC charging (default for USER_LOGGED, private) → 0.9 efficiency
        BigDecimal expected = new BigDecimal("40.0").multiply(BigDecimal.valueOf(0.9));
        assertEquals(0, expected.compareTo(service.effectiveKwhForConsumption(log)));
    }

    @Test
    void effectiveKwhForConsumption_prefersKwhAtVehicle_overKwhCharged() {
        EvLog log = log(new BigDecimal("38.0"), new BigDecimal("42.0"));
        assertEquals(0, new BigDecimal("38.0").compareTo(service.effectiveKwhForConsumption(log)));
    }

    private EvLog log(BigDecimal kwhAtVehicle, BigDecimal kwhCharged) {
        LocalDateTime now = LocalDateTime.now();
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .kwhAtVehicle(kwhAtVehicle)
                .costEur(BigDecimal.TEN)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.AC)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
