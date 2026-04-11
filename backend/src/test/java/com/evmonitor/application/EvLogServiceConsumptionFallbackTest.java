package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit Tests for EvLogService.calculateConsumptionFallback()
 * Tests the fallback formula: (total kWh charged / total distance) × 100
 */
@ExtendWith(MockitoExtension.class)
class EvLogServiceConsumptionFallbackTest {

    @Mock
    private EvLogRepository evLogRepository;
    @Mock
    private CarRepository carRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CoinLogService coinLogService;
    @Mock
    private TemperatureEnrichmentService temperatureEnrichmentService;

    private EvLogService evLogService;
    private UUID carId;

    @BeforeEach
    void setUp() {
        PlausibilityProperties props = new PlausibilityProperties();
        props.setAcChargingEfficiency(1.0);
        props.setDcChargingEfficiency(1.0);
        evLogService = new EvLogService(evLogRepository, carRepository, userRepository, coinLogService, temperatureEnrichmentService, mock(VehicleSpecificationRepository.class), props, mock(com.evmonitor.domain.BatterySohRepository.class));
        carId = UUID.randomUUID();
    }

    @Test
    void shouldCalculateCorrectConsumption_standardCase() {
        // Given: 20 kWh charged, 100 km driven
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("20.0"))
        );
        BigDecimal distanceKm = new BigDecimal("100");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (20 × 100) / 100 = 20.0 kWh/100km
        assertEquals(new BigDecimal("20.00"), consumption);
    }

    @Test
    void shouldCalculateConsumption_lowConsumption() {
        // Given: Efficient summer driving (12 kWh/100km)
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("24.0"))
        );
        BigDecimal distanceKm = new BigDecimal("200");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (24 × 100) / 200 = 12.0 kWh/100km
        assertEquals(new BigDecimal("12.00"), consumption);
    }

    @Test
    void shouldCalculateConsumption_highConsumption() {
        // Given: Winter driving with heater (30 kWh/100km)
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("30.0"))
        );
        BigDecimal distanceKm = new BigDecimal("100");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (30 × 100) / 100 = 30.0 kWh/100km
        assertEquals(new BigDecimal("30.00"), consumption);
    }

    @Test
    void shouldSumMultipleLogs() {
        // Given: Multiple charges, total 45 kWh over 200km
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("20.0")),
            createLog(new BigDecimal("15.0")),
            createLog(new BigDecimal("10.0"))
        );
        BigDecimal distanceKm = new BigDecimal("200");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (45 × 100) / 200 = 22.5 kWh/100km
        assertEquals(new BigDecimal("22.50"), consumption);
    }

    @Test
    void shouldRoundToTwoDecimals_repeatingDecimal() {
        // Given: Distance that creates repeating decimal
        // 67 kWh, 333 km → 20.12012012... kWh/100km
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("67.0"))
        );
        BigDecimal distanceKm = new BigDecimal("333");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: Should round to 20.12
        assertEquals(new BigDecimal("20.12"), consumption);
        assertEquals(2, consumption.scale());
    }

    @Test
    void shouldRoundUp_whenThirdDecimalIsAboveFive() {
        // Given: 15.556 kWh/100km → should round to 15.56
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("14.0"))
        );
        BigDecimal distanceKm = new BigDecimal("90");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (14 × 100) / 90 = 15.555... → 15.56
        assertEquals(new BigDecimal("15.56"), consumption);
    }

    @Test
    void shouldHandleSmallCharges_preconditioning() {
        // Given: Preconditioning, only 0.5 kWh for 5 km
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("0.5"))
        );
        BigDecimal distanceKm = new BigDecimal("5");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (0.5 × 100) / 5 = 10.0 kWh/100km
        assertEquals(new BigDecimal("10.00"), consumption);
    }

    @Test
    void shouldHandleLargeCharges_roadTrip() {
        // Given: Full charge, 150 kWh for 750 km
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("150.0"))
        );
        BigDecimal distanceKm = new BigDecimal("750");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (150 × 100) / 750 = 20.0 kWh/100km
        assertEquals(new BigDecimal("20.00"), consumption);
    }

    @Test
    void shouldReturnNull_whenDistanceIsZero() {
        // Given: No distance driven (car didn't move)
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("10.0"))
        );
        BigDecimal distanceKm = BigDecimal.ZERO;

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: Cannot calculate consumption without distance
        assertNull(consumption);
    }

    @Test
    void shouldReturnNull_whenDistanceIsNegative() {
        // Given: Invalid negative distance
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("10.0"))
        );
        BigDecimal distanceKm = new BigDecimal("-50");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: Invalid input should return null
        assertNull(consumption);
    }

    @Test
    void shouldReturnNull_whenDistanceIsNull() {
        // Given: No odometer data available
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("10.0"))
        );
        BigDecimal distanceKm = null;

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: Cannot calculate without distance
        assertNull(consumption);
    }

    @Test
    void shouldHandleEmptyLogsList() {
        // Given: No logs
        List<EvLog> logs = new ArrayList<>();
        BigDecimal distanceKm = new BigDecimal("100");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: 0 kWh / 100 km = 0.00 kWh/100km
        assertEquals(new BigDecimal("0.00"), consumption);
    }

    @Test
    void shouldCalculateWithDecimalInputs() {
        // Given: Precise measurements with decimals
        List<EvLog> logs = List.of(
            createLog(new BigDecimal("45.7"))
        );
        BigDecimal distanceKm = new BigDecimal("234.5");

        // When
        BigDecimal consumption = evLogService.calculateConsumptionFallback(logs, distanceKm);

        // Then: (45.7 × 100) / 234.5 = 19.48... → 19.49
        assertEquals(new BigDecimal("19.49"), consumption);
    }

    /**
     * Helper to create a minimal EvLog with only kWh charged (other fields are dummy values).
     */
    private EvLog createLog(BigDecimal kwhCharged) {
        return EvLog.createNew(
            carId,
            kwhCharged,
            BigDecimal.TEN,
            60,
            "test-connector",
            null,
            BigDecimal.TEN,
            null,
            LocalDateTime.now(),
            ChargingType.UNKNOWN,
            null, null
        );
    }
}
