package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for EvLogService.
 * Tests charging log creation, ownership checks, and statistics calculation.
 *
 * PRIVACY CRITICAL: Geohashing must work correctly to protect user location!
 * GPS coordinates must NEVER be stored in the database!
 */
@ExtendWith(MockitoExtension.class)
class EvLogServiceTest {

    @Mock
    private EvLogRepository evLogRepository;

    @Mock
    private CarRepository carRepository;

    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;
    private Car testCar;

    @BeforeEach
    void setUp() {
        evLogService = new EvLogService(evLogRepository, carRepository);

        userId = UUID.randomUUID();
        carId = UUID.randomUUID();
        testCar = TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3);
    }

    @Test
    void shouldConvertLatLonToGeohash_PrivacyCritical() {
        // Given: GPS coordinates (Berlin Mitte)
        double latitude = 52.5200;
        double longitude = 13.4050;
        String expectedGeohash = GeoHash.withCharacterPrecision(latitude, longitude, 5).toBase32();

        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                latitude,
                longitude,
                LocalDateTime.now()
        );

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.save(any(EvLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        evLogService.logCharging(userId, request);

        // Then
        ArgumentCaptor<EvLog> logCaptor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(logCaptor.capture());
        EvLog savedLog = logCaptor.getValue();

        // PRIVACY CHECK: Only geohash is stored, NOT lat/lon!
        assertNotNull(savedLog.getGeohash());
        assertEquals(5, savedLog.getGeohash().length(), "Geohash must be 5 characters (~5km precision)");
        assertEquals(expectedGeohash, savedLog.getGeohash());

        // Verify lat/lon are NOT stored (EvLog entity has no lat/lon fields)
        // If this test compiles, we're safe - EvLog has no lat/lon fields to accidentally store
    }

    @Test
    void shouldHandleNullLatLon() {
        // Given: No GPS coordinates provided
        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null, // No latitude
                null, // No longitude
                LocalDateTime.now()
        );

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.save(any(EvLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        evLogService.logCharging(userId, request);

        // Then
        ArgumentCaptor<EvLog> logCaptor = ArgumentCaptor.forClass(EvLog.class);
        verify(evLogRepository).save(logCaptor.capture());
        EvLog savedLog = logCaptor.getValue();

        // Geohash should be null when no coordinates provided
        assertNull(savedLog.getGeohash());
    }

    @Test
    void shouldEnforceOwnership_UserCanOnlyLogForOwnCars() {
        // Given: User tries to log for a car they don't own
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null,
                null,
                LocalDateTime.now()
        );

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            evLogService.logCharging(userId, request);
        });

        assertEquals("User does not own the specified car", exception.getMessage());

        // Verify log was NOT saved
        verify(evLogRepository, never()).save(any(EvLog.class));
    }

    @Test
    void shouldRejectLoggingForNonExistentCar() {
        // Given
        UUID nonExistentCarId = UUID.randomUUID();
        EvLogRequest request = new EvLogRequest(
                nonExistentCarId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null,
                null,
                LocalDateTime.now()
        );

        when(carRepository.findById(nonExistentCarId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            evLogService.logCharging(userId, request);
        });
    }

    @Test
    void shouldCalculateStatistics_WithMultipleLogs() {
        // Given: Multiple charging logs for a car
        UUID logId1 = UUID.randomUUID();
        UUID logId2 = UUID.randomUUID();
        UUID logId3 = UUID.randomUUID();

        EvLog log1 = new EvLog(logId1, carId, new BigDecimal("50.0"), new BigDecimal("12.50"),
                60, "u33db", LocalDateTime.now().minusDays(3), LocalDateTime.now(), LocalDateTime.now());
        EvLog log2 = new EvLog(logId2, carId, new BigDecimal("30.0"), new BigDecimal("9.00"),
                45, "u33dc", LocalDateTime.now().minusDays(2), LocalDateTime.now(), LocalDateTime.now());
        EvLog log3 = new EvLog(logId3, carId, new BigDecimal("20.0"), new BigDecimal("5.00"),
                30, "u33dd", LocalDateTime.now().minusDays(1), LocalDateTime.now(), LocalDateTime.now());

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(log1, log2, log3));

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, "DAY");

        // Then
        assertNotNull(stats);
        assertEquals(3, stats.totalCharges());
        assertEquals(new BigDecimal("100.0"), stats.totalKwhCharged()); // 50 + 30 + 20
        assertEquals(new BigDecimal("26.50"), stats.totalCostEur()); // 12.50 + 9.00 + 5.00

        // Average cost per kWh = 26.50 / 100.0 = 0.27 (rounded to 2 decimals)
        assertEquals(new BigDecimal("0.27"), stats.avgCostPerKwh());

        // Cheapest and most expensive
        assertEquals(new BigDecimal("5.00"), stats.cheapestChargeEur());
        assertEquals(new BigDecimal("12.50"), stats.mostExpensiveChargeEur());

        // Average duration = (60 + 45 + 30) / 3 = 45
        assertEquals(45, stats.avgChargeDurationMinutes());
    }

    @Test
    void shouldReturnEmptyStatistics_WhenNoLogs() {
        // Given: No logs for this car
        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of());

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, "MONTH");

        // Then
        assertNotNull(stats);
        assertEquals(0, stats.totalCharges());
        assertEquals(BigDecimal.ZERO, stats.totalKwhCharged());
        assertEquals(BigDecimal.ZERO, stats.totalCostEur());
        assertEquals(BigDecimal.ZERO, stats.avgCostPerKwh());
        assertEquals(BigDecimal.ZERO, stats.cheapestChargeEur());
        assertEquals(BigDecimal.ZERO, stats.mostExpensiveChargeEur());
        assertEquals(0, stats.avgChargeDurationMinutes());
        assertTrue(stats.chargesOverTime().isEmpty());
    }

    @Test
    void shouldFilterStatisticsByDateRange() {
        // Given: Logs in different time periods
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldLog = now.minusDays(30); // Outside range
        LocalDateTime recentLog1 = now.minusDays(5); // Inside range
        LocalDateTime recentLog2 = now.minusDays(2); // Inside range

        EvLog log1 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("50.0"), new BigDecimal("10.00"),
                60, "u33db", oldLog, now, now);
        EvLog log2 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("30.0"), new BigDecimal("8.00"),
                45, "u33dc", recentLog1, now, now);
        EvLog log3 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("20.0"), new BigDecimal("6.00"),
                30, "u33dd", recentLog2, now, now);

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(log1, log2, log3));

        // When: Filter last 7 days
        LocalDate startDate = now.minusDays(7).toLocalDate();
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, startDate, null, "DAY");

        // Then: Only logs 2 and 3 should be included
        assertEquals(2, stats.totalCharges());
        assertEquals(new BigDecimal("50.0"), stats.totalKwhCharged()); // 30 + 20
        assertEquals(new BigDecimal("14.00"), stats.totalCostEur()); // 8.00 + 6.00
    }

    @Test
    void shouldEnforceOwnership_ForStatistics() {
        // Given: User tries to get statistics for a car they don't own
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            evLogService.getStatistics(carId, userId, null, null, "MONTH");
        });
    }

    @Test
    void shouldGroupByDay_ForStatistics() {
        // Given: Logs on different days
        LocalDateTime day1 = LocalDateTime.of(2025, 2, 1, 10, 0);
        LocalDateTime day2 = LocalDateTime.of(2025, 2, 2, 14, 0);
        LocalDateTime day3 = LocalDateTime.of(2025, 2, 3, 18, 0);

        EvLog log1 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("50.0"), new BigDecimal("10.00"),
                60, "u33db", day1, day1, day1);
        EvLog log2 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("30.0"), new BigDecimal("8.00"),
                45, "u33dc", day2, day2, day2);
        EvLog log3 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("20.0"), new BigDecimal("6.00"),
                30, "u33dd", day3, day3, day3);

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(log1, log2, log3));

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, "DAY");

        // Then: Should have 3 data points (one per day)
        assertEquals(3, stats.chargesOverTime().size());
    }

    @Test
    void shouldGroupByMonth_ForStatistics() {
        // Given: Logs in different months
        LocalDateTime jan = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime feb = LocalDateTime.of(2025, 2, 10, 14, 0);

        EvLog log1 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("50.0"), new BigDecimal("10.00"),
                60, "u33db", jan, jan, jan);
        EvLog log2 = new EvLog(UUID.randomUUID(), carId, new BigDecimal("30.0"), new BigDecimal("8.00"),
                45, "u33dc", feb, feb, feb);

        when(carRepository.findById(carId)).thenReturn(Optional.of(testCar));
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(log1, log2));

        // When
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, userId, null, null, "MONTH");

        // Then: Should have 2 data points (one per month)
        assertEquals(2, stats.chargesOverTime().size());
    }
}
