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
        return new User(
                userId,
                email,
                username,
                passwordHash,
                AuthProvider.LOCAL,
                "USER",
                true, // emailVerified
                false, // seedData
                true, // emailNotificationsEnabled
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
                null, // referredByUserId
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    /**
     * Create a test car for a user.
     */
    public static Car createTestCar(UUID userId, CarBrand.CarModel model, BigDecimal batteryCapacityKwh) {
        return Car.createNew(
                userId,
                model,
                2024,
                "TEST-123",
                "Standard",
                batteryCapacityKwh,
                new BigDecimal("150.0")
        );
    }

    /**
     * Create a test car with custom ID (for testing queries).
     */
    public static Car createTestCarWithId(UUID carId, UUID userId, CarBrand.CarModel model) {
        return new Car(
                carId,
                userId,
                model,
                2024,
                "TEST-456",
                "Performance",
                new BigDecimal("75.0"),
                new BigDecimal("200.0"),
                LocalDate.now(),
                null,
                CarStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,   // imagePath
                false,  // imagePublic
                false   // isPrimary
        );
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
                null, // odometerKm (optional)
                null, // maxChargingPowerKw (optional)
                LocalDateTime.now()
        );
    }

    /**
     * Create a test charging log with custom timestamp (for statistics tests).
     */
    public static EvLog createTestEvLogWithTimestamp(UUID carId, BigDecimal kwhCharged,
                                                      BigDecimal costEur, LocalDateTime timestamp) {
        return EvLog.createNew(
                carId,
                kwhCharged,
                costEur,
                60,
                "u33dc",
                null, // odometerKm (optional)
                null, // maxChargingPowerKw (optional)
                timestamp
        );
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
