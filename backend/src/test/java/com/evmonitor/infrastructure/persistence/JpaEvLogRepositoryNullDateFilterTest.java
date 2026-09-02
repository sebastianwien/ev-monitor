package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for PostgreSQL "could not determine data type of parameter"
 * when from/to date filters are null.
 *
 * Root cause: (:from IS NULL OR ...) sends an untyped null - fixed by cast(:from as LocalDateTime).
 */
@Disabled("CI: Testcontainers/Docker not reliably available - run locally")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.context.ActiveProfiles("test")
class JpaEvLogRepositoryNullDateFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.pool.size", () -> "0");
    }

    @Autowired
    private JpaEvLogRepository evLogRepository;

    @Autowired
    private JpaCarRepository carRepository;

    @Autowired
    private JpaUserRepository userRepository;

    private UUID carId;

    @BeforeEach
    void setup() {
        evLogRepository.deleteAll();
        carRepository.deleteAll();
        userRepository.deleteAll();

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("test@ev-monitor.net");
        user.setUsername("testuser");
        user.setPasswordHash("hash");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setSeedData(false);
        user.setLeaderboardVisible(true);
        user.setReferralCode("TESTCODE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        carId = UUID.randomUUID();
        CarEntity car = new CarEntity();
        car.setId(carId);
        car.setUserId(userId);
        car.setModel(CarBrand.CarModel.MODEL_3);
        car.setYear(2023);
        car.setLicensePlate("EVM-1");
        car.setTrim("LR");
        car.setCustomNetCapacityKwh(new BigDecimal("75.0"));
        car.setPowerKw(new BigDecimal("150.0"));
        car.setStatus(CarStatus.ACTIVE);
        car.setCreatedAt(LocalDateTime.now());
        car.setUpdatedAt(LocalDateTime.now());
        carRepository.save(car);

        EvLogEntity log = new EvLogEntity();
        log.setId(UUID.randomUUID());
        log.setCarId(carId);
        log.setKwhCharged(new BigDecimal("30.0"));
        log.setChargeDurationMinutes(60);
        log.setLoggedAt(LocalDateTime.now());
        log.setDataSource("USER_LOGGED");
        log.setIncludeInStatistics(true);
        log.setChargingType("AC");
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        evLogRepository.save(log);
    }

    @Test
    void findPagedByCarId_withBothDatesNull_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.findPagedByCarId(carId, null, null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void findPagedByCarId_withBothDatesNull_returnsResults() {
        List<EvLogEntity> results = evLogRepository.findPagedByCarId(carId, null, null, PageRequest.of(0, 20));
        assertThat(results).hasSize(1);
    }

    @Test
    void findPagedByCarId_withFromOnly_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.findPagedByCarId(carId, LocalDateTime.now().minusDays(1), null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void findPagedByCarId_withToOnly_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.findPagedByCarId(carId, null, LocalDateTime.now().plusDays(1), PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void countByCarIdAndDateRange_withBothDatesNull_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.countByCarIdAndDateRange(carId, null, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void countByCarIdAndDateRange_withBothDatesNull_returnsCount() {
        long count = evLogRepository.countByCarIdAndDateRange(carId, null, null);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void findPagedByCarIds_withBothDatesNull_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.findPagedByCarIds(List.of(carId), null, null, PageRequest.of(0, 20))
        ).doesNotThrowAnyException();
    }

    @Test
    void countByCarIdsAndDateRange_withBothDatesNull_doesNotThrow() {
        assertThatCode(() ->
            evLogRepository.countByCarIdsAndDateRange(List.of(carId), null, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void findPricelessByCarId_returnsOnlyCostlessLogs_newestFirst() {
        // setup() persisted one cost-less log (loggedAt = now). Add a priced one and an older cost-less one.
        EvLogEntity priced = costLog(new BigDecimal("12.00"), LocalDateTime.now().minusDays(2));
        evLogRepository.save(priced);
        EvLogEntity olderFree = costLog(null, LocalDateTime.now().minusDays(1));
        evLogRepository.save(olderFree);

        List<EvLogEntity> res = evLogRepository.findByCarIdAndCostEurIsNullOrderByLoggedAtDesc(carId);

        assertThat(res).hasSize(2); // setup log (now) + olderFree; priced excluded
        assertThat(res).allMatch(l -> l.getCostEur() == null);
        assertThat(res.get(0).getLoggedAt()).isAfter(res.get(1).getLoggedAt()); // newest first
    }

    private EvLogEntity costLog(BigDecimal costEur, LocalDateTime loggedAt) {
        EvLogEntity log = new EvLogEntity();
        log.setId(UUID.randomUUID());
        log.setCarId(carId);
        log.setKwhCharged(new BigDecimal("30.0"));
        log.setCostEur(costEur);
        log.setChargeDurationMinutes(60);
        log.setLoggedAt(loggedAt);
        log.setDataSource("WALLBOX_GOE");
        log.setIncludeInStatistics(true);
        log.setChargingType("AC");
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        return log;
    }
}
