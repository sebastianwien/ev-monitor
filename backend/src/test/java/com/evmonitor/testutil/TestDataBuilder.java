package com.evmonitor.testutil;

import com.evmonitor.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test Data Builder for creating test entities.
 * Centralizes test data creation to avoid duplication and ensure consistency.
 */
public class TestDataBuilder {

    /**
     * Create a test user with default values.
     * Password is "TestPassword123" hashed with BCrypt.
     * Username is derived from email (part before @).
     */
    public static User createTestUser(String email) {
        // BCrypt hash of "TestPassword123" (strength 10)
        // Pre-verified so tests don't need to go through email verification flow
        String username = email.split("@")[0];
        return User.createVerifiedLocalUser(email, username, "$2a$10$N9qo8uLOickgx2ZMRZoMye7JU5qBvJqLzL/MQPVxqNGQqQfqzZ5bC");
    }

    /**
     * Create a test user with custom ID (for testing ownership checks).
     */
    public static User createTestUserWithId(UUID userId, String email, String passwordHash) {
        String username = email.split("@")[0];
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(userId)
                .email(email).username(username).passwordHash(passwordHash)
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .createdAt(now).updatedAt(now)
                .build();
    }

    /**
     * Create a test car for a user.
     */
    public static Car createTestCar(UUID userId, CarBrand.CarModel model, BigDecimal customNetCapacityKwh) {
        return Car.createNew(
                userId,
                model,
                2024,
                "TEST-123",
                "Standard",
                customNetCapacityKwh,
                new BigDecimal("150.0"),
                null
        );
    }

    /**
     * Create a test car with custom ID (for testing queries).
     */
    public static Car createTestCarWithId(UUID carId, UUID userId, CarBrand.CarModel model) {
        return Car.builder()
                .id(carId).userId(userId).model(model).year(2024)
                .licensePlate("TEST-456").trim("Performance")
                .customNetCapacityKwh(new BigDecimal("75.0")).powerKw(new BigDecimal("200.0"))
                .registrationDate(LocalDate.now()).status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test charging log.
     */
    public static EvLog createTestEvLog(UUID carId, BigDecimal kwhCharged, BigDecimal costEur) {
        return EvLog.createNew(
                carId,
                kwhCharged,
                costEur,
                45,
                "u33db", // Berlin Mitte geohash (5-char)
                50000, // odometerKm (required for tests)
                null, // maxChargingPowerKw (optional)
                new java.math.BigDecimal("80"), // socAfterChargePercent (required for tests)
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                null, null,
                false, null
        );
    }

    /**
     * Create a test charging log with custom timestamp (for statistics tests).
     */
    public static EvLog createTestEvLogWithTimestamp(UUID carId, BigDecimal kwhCharged,
                                                      BigDecimal costEur, LocalDateTime timestamp) {
        return createTestEvLogWithTimestampAndOdometer(carId, kwhCharged, costEur, timestamp, 50000);
    }

    public static EvLog createTestEvLogWithTimestampAndOdometer(UUID carId, BigDecimal kwhCharged,
                                                                 BigDecimal costEur, LocalDateTime timestamp,
                                                                 Integer odometerKm) {
        return EvLog.createNew(
                carId,
                kwhCharged,
                costEur,
                60,
                "u33dc",
                odometerKm,
                null, // maxChargingPowerKw (optional)
                new java.math.BigDecimal("80"), // socAfterChargePercent (required for tests)
                timestamp,
                ChargingType.UNKNOWN,
                null, null,
                false, null
        );
    }

    /**
     * Smartcar-Stil-Log: kWh nur am Fahrzeug gemessen (kwh_at_vehicle gefuellt,
     * kwh_charged bleibt NULL durch die Builder-Normalisierung bei AT_VEHICLE).
     */
    public static EvLog createSmartcarStyleLog(UUID carId, LocalDateTime timestamp,
                                                BigDecimal kwhAtVehicle, BigDecimal costEur,
                                                ChargingType chargingType, Integer odometerKm) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return com.evmonitor.domain.EvLog.builder()
                .id(java.util.UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhAtVehicle)
                .costEur(costEur)
                .chargeDurationMinutes(60)
                .geohash("u33dc")
                .odometerKm(odometerKm)
                .socAfterChargePercent(new BigDecimal("80"))
                .loggedAt(timestamp)
                .dataSource(com.evmonitor.domain.DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .chargingType(chargingType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Create a test WLTP vehicle specification.
     */
    public static VehicleSpecification createTestVehicleSpecification(
            String brand, String model, BigDecimal batteryCapacityKwh) {
        return VehicleSpecification.createNew(
                brand,
                model,
                batteryCapacityKwh,
                new BigDecimal("450.0"), // 450 km range
                new BigDecimal("16.5"),   // 16.5 kWh/100km
                VehicleSpecification.WltpType.COMBINED
        );
    }

    /**
     * Create a test coin log.
     */
    public static CoinLog createTestCoinLog(UUID userId, CoinType coinType, int amount, String description) {
        return CoinLog.createNew(userId, coinType, amount, description);
    }
}
