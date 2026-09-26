package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Forum-Bug nach Soft-Delete (V188): die Unique-Constraint aus V24 zählte Tombstones mit, ein
 * gelöschter Ladevorgang blockierte das erneute Eintragen zur selben Uhrzeit. V190 ersetzt sie
 * durch einen partiellen Unique-Index auf aktive Zeilen. Nur gegen Postgres mit Flyway prüfbar,
 * die H2-Tests kennen die Constraint nicht. Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class EvLogTombstoneUniquePostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 20, 18, 30);

    @Autowired EvLogService evLogService;
    @Autowired EvLogRepository evLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("tomb-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    @Test
    void deletedLog_canBeReenteredAtSameTime() {
        EvLog deleted = createLog();
        evLogRepository.softDelete(deleted.getId());

        var created = evLogService.logCharging(user.getId(), requestAt(AT));

        assertThat(created).isNotNull();
        assertThat(evLogRepository.findAllByCarId(car.getId())).extracting(EvLog::getLoggedAt).containsExactly(AT);
        assertThat(evLogRepository.findByIdIncludingDeleted(deleted.getId())).isPresent();
    }

    @Test
    void restore_whenLiveDuplicateExists_throwsConflictAndStaysDeleted() {
        EvLog deleted = createLog();
        evLogRepository.softDelete(deleted.getId());
        evLogService.logCharging(user.getId(), requestAt(AT));

        assertThatThrownBy(() -> evLogService.restoreLog(deleted.getId(), user.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(evLogRepository.findById(deleted.getId())).isEmpty();
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(1);
    }

    private EvLog createLog() {
        return evLogRepository.save(EvLog.createNewWithSource(
                car.getId(), new BigDecimal("30.0"), new BigDecimal("12.00"), 60, "u33d1",
                null, null, null, AT, DataSource.USER_LOGGED, ChargingType.UNKNOWN, null));
    }

    private EvLogRequest requestAt(LocalDateTime loggedAt) {
        return new EvLogRequest(car.getId(), new BigDecimal("30.0"), new BigDecimal("12.00"), 60,
                null, null, 50000, null, new BigDecimal("80"), loggedAt, null, null, null, null);
    }
}
