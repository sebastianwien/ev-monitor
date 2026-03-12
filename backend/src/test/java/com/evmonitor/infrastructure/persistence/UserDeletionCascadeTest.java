package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSGVO Compliance Test: Verifies that deleting a user CASCADE deletes ALL associated data.
 *
 * Uses a real PostgreSQL container (Testcontainers) with the actual Flyway migrations,
 * so this test proves that CASCADE DELETE works exactly as it does on production.
 *
 * If Docker is not available, these tests are automatically skipped.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class UserDeletionCascadeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaCarRepository carRepository;

    @Autowired
    private JpaEvLogRepository evLogRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        evLogRepository.deleteAll();
        carRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        carId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("cascade-test@example.com");
        user.setUsername("cascadeuser");
        user.setPasswordHash("hashedPassword");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setSeedData(false);
        user.setReferralCode("CASC0001");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        CarEntity car = new CarEntity();
        car.setId(carId);
        car.setUserId(userId);
        car.setModel(CarBrand.CarModel.MODEL_3);
        car.setYear(2023);
        car.setLicensePlate("TEST-123");
        car.setTrim("Long Range");
        car.setBatteryCapacityKwh(new BigDecimal("75.0"));
        car.setPowerKw(new BigDecimal("283"));
        car.setStatus(CarStatus.ACTIVE);
        car.setCreatedAt(LocalDateTime.now());
        car.setUpdatedAt(LocalDateTime.now());
        carRepository.save(car);

        EvLogEntity log = new EvLogEntity();
        log.setId(UUID.randomUUID());
        log.setCarId(carId);
        log.setKwhCharged(new BigDecimal("45.5"));
        log.setCostEur(new BigDecimal("18.20"));
        log.setGeohash("u33d1");
        log.setChargeDurationMinutes(120);
        log.setLoggedAt(LocalDateTime.now());
        log.setDataSource("USER_LOGGED");
        log.setIncludeInStatistics(true);
        log.setChargingType("AC");
        log.setRawImportData(null);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        evLogRepository.save(log);
    }

    @Test
    void deletingUser_shouldCascadeDeleteCarsAndEvLogs() {
        assertTrue(userRepository.existsById(userId));
        assertTrue(carRepository.existsById(carId));
        assertEquals(1, evLogRepository.findAllByUserId(userId).size());

        // Only delete the user - DB CASCADE handles the rest (like production)
        userRepository.deleteById(userId);

        assertFalse(userRepository.existsById(userId), "User should be deleted");
        assertFalse(carRepository.existsById(carId), "Car should be CASCADE deleted");
        assertEquals(0, evLogRepository.findAllByUserId(userId).size(), "EvLogs should be CASCADE deleted");
    }

    @Test
    void deletingUser_withMultipleCarsAndLogs_shouldDeleteEverything() {
        UUID car2Id = UUID.randomUUID();
        CarEntity car2 = new CarEntity();
        car2.setId(car2Id);
        car2.setUserId(userId);
        car2.setModel(CarBrand.CarModel.ID_3);
        car2.setYear(2023);
        car2.setLicensePlate("TEST-456");
        car2.setTrim("Pro");
        car2.setBatteryCapacityKwh(new BigDecimal("58.0"));
        car2.setPowerKw(new BigDecimal("204"));
        car2.setStatus(CarStatus.ACTIVE);
        car2.setCreatedAt(LocalDateTime.now());
        car2.setUpdatedAt(LocalDateTime.now());
        carRepository.save(car2);

        EvLogEntity log2 = new EvLogEntity();
        log2.setId(UUID.randomUUID());
        log2.setCarId(car2Id);
        log2.setKwhCharged(new BigDecimal("30.0"));
        log2.setCostEur(new BigDecimal("12.50"));
        log2.setGeohash("u33d2");
        log2.setChargeDurationMinutes(90);
        log2.setLoggedAt(LocalDateTime.now());
        log2.setDataSource("USER_LOGGED");
        log2.setIncludeInStatistics(true);
        log2.setChargingType("DC");
        log2.setRawImportData(null);
        log2.setCreatedAt(LocalDateTime.now());
        log2.setUpdatedAt(LocalDateTime.now());
        evLogRepository.save(log2);

        assertEquals(2, carRepository.findAllByUserId(userId).size());
        assertEquals(2, evLogRepository.findAllByUserId(userId).size());

        // Only delete the user - DB CASCADE handles the rest
        userRepository.deleteById(userId);

        assertFalse(userRepository.existsById(userId));
        assertEquals(0, carRepository.findAllByUserId(userId).size(), "All cars should be CASCADE deleted");
        assertEquals(0, evLogRepository.findAllByUserId(userId).size(), "All EvLogs should be CASCADE deleted");
    }

    @Test
    void deletingUser_shouldNotAffectOtherUsers() {
        UUID otherUserId = UUID.randomUUID();
        UserEntity otherUser = new UserEntity();
        otherUser.setId(otherUserId);
        otherUser.setEmail("other@example.com");
        otherUser.setUsername("otheruser");
        otherUser.setPasswordHash("hashedPassword");
        otherUser.setAuthProvider(AuthProvider.LOCAL);
        otherUser.setRole("USER");
        otherUser.setEmailVerified(true);
        otherUser.setSeedData(false);
        otherUser.setReferralCode("CASC0002");
        otherUser.setCreatedAt(LocalDateTime.now());
        otherUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(otherUser);

        UUID otherCarId = UUID.randomUUID();
        CarEntity otherCar = new CarEntity();
        otherCar.setId(otherCarId);
        otherCar.setUserId(otherUserId);
        otherCar.setModel(CarBrand.CarModel.MODEL_Y);
        otherCar.setYear(2024);
        otherCar.setLicensePlate("OTHER-789");
        otherCar.setTrim("AWD");
        otherCar.setBatteryCapacityKwh(new BigDecimal("75.0"));
        otherCar.setPowerKw(new BigDecimal("384"));
        otherCar.setStatus(CarStatus.ACTIVE);
        otherCar.setCreatedAt(LocalDateTime.now());
        otherCar.setUpdatedAt(LocalDateTime.now());
        carRepository.save(otherCar);

        // Only delete the first user
        userRepository.deleteById(userId);

        assertFalse(userRepository.existsById(userId), "Deleted user should be gone");
        assertFalse(carRepository.existsById(carId), "Deleted user's car should be CASCADE deleted");

        assertTrue(userRepository.existsById(otherUserId), "Other user should still exist");
        assertTrue(carRepository.existsById(otherCarId), "Other user's car should still exist");
    }
}
