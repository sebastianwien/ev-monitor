package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository.RecapCandidate;
import com.evmonitor.testutil.SharedPostgres;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auf echtem Postgres, weil die Abfrage DISTINCT ON und ON CONFLICT nutzt (H2 kann beides nicht).
 * Ohne umschließende Transaktion: das Repository liest per JdbcTemplate und sähe ungeflushte
 * JPA-Saves nicht. Die Datenbank ist pro Testklasse frisch, eindeutige Mails trennen die Fälle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class MonthlyRecapQueryRepositoryPostgresIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        SharedPostgres.registerMigrated(registry);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static final LocalDate MONTH = LocalDate.of(2024, 8, 1);

    @Autowired MonthlyRecapQueryRepository repository;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired EvLogRepository evLogRepository;

    @Test
    void userWithThreeLogsInMonth_isCandidateWithThatCar() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = saveCar(user);
        saveLogs(car, 3, MONTH.atTime(12, 0));

        assertThat(repository.findCandidates(MONTH))
                .contains(new RecapCandidate(user.getId(), car.getId()));
    }

    @Test
    void userWithTwoLogsInMonth_isNotCandidate() {
        User user = createAndSaveUser(uniqueEmail());
        saveLogs(saveCar(user), 2, MONTH.atTime(12, 0));

        assertThat(candidateUserIds()).doesNotContain(user.getId());
    }

    @Test
    void logsOutsideTheMonth_doNotCount() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = saveCar(user);
        saveLogs(car, 2, MONTH.atTime(12, 0));
        saveLog(car, MONTH.minusDays(1).atTime(23, 59));
        saveLog(car, MONTH.plusMonths(1).atStartOfDay());

        assertThat(candidateUserIds()).doesNotContain(user.getId());
    }

    @Test
    void lastMinuteOfMonth_counts() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = saveCar(user);
        saveLogs(car, 2, MONTH.atTime(12, 0));
        saveLog(car, MONTH.plusMonths(1).atStartOfDay().minusMinutes(1));

        assertThat(candidateUserIds()).contains(user.getId());
    }

    @Test
    void twoCars_picksTheOneWithMoreLogs() {
        User user = createAndSaveUser(uniqueEmail());
        Car small = saveCar(user);
        Car busy = saveCar(user);
        saveLogs(small, 3, MONTH.atTime(8, 0));
        saveLogs(busy, 5, MONTH.atTime(9, 0));

        List<RecapCandidate> forUser = repository.findCandidates(MONTH).stream()
                .filter(c -> c.userId().equals(user.getId()))
                .toList();

        assertThat(forUser).containsExactly(new RecapCandidate(user.getId(), busy.getId()));
    }

    @Test
    void notificationsDisabled_isNotCandidate() {
        User user = createAndSaveUser(uniqueEmail());
        saveLogs(saveCar(user), 3, MONTH.atTime(12, 0));
        userRepository.disableEmailNotifications(user.getId());

        assertThat(candidateUserIds()).doesNotContain(user.getId());
    }

    @Test
    void softDeletedLogs_doNotCount() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = saveCar(user);
        saveLogs(car, 2, MONTH.atTime(12, 0));
        EvLog deleted = saveLog(car, MONTH.atTime(18, 0));
        evLogRepository.softDelete(deleted.getId());

        assertThat(candidateUserIds()).doesNotContain(user.getId());
    }

    @Test
    void claim_isExclusivePerUserAndMonth_untilReleased() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = saveCar(user);
        saveLogs(car, 3, MONTH.atTime(12, 0));

        assertThat(repository.claim(user.getId(), MONTH, car.getId())).isTrue();
        assertThat(repository.claim(user.getId(), MONTH, car.getId())).isFalse();
        assertThat(candidateUserIds()).doesNotContain(user.getId());

        repository.release(user.getId(), MONTH);

        assertThat(candidateUserIds()).contains(user.getId());
        assertThat(repository.claim(user.getId(), MONTH, car.getId())).isTrue();
    }

    // --- helpers ---

    private List<UUID> candidateUserIds() {
        return repository.findCandidates(MONTH).stream().map(RecapCandidate::userId).toList();
    }

    private User createAndSaveUser(String email) {
        return userRepository.save(TestDataBuilder.createTestUser(email));
    }

    private Car saveCar(User user) {
        return carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
    }

    private void saveLogs(Car car, int count, LocalDateTime first) {
        for (int i = 0; i < count; i++) {
            saveLog(car, first.plusDays(i));
        }
    }

    private EvLog saveLog(Car car, LocalDateTime at) {
        return evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                car.getId(), BigDecimal.valueOf(30), BigDecimal.valueOf(8), at));
    }

    private String uniqueEmail() {
        return "recap-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
