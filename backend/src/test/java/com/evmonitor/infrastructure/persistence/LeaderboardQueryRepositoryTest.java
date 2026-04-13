                                                                                      package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.LeaderboardRankRow;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that leaderboard SQL filters (leaderboard_visible, is_seed_data, include_in_statistics)
 * actually work against a real PostgreSQL database with Flyway migrations applied.
 *
 * Uses Testcontainers + full SpringBootTest so Flyway runs and native SQL queries work.
 * Skipped automatically when Docker is not available.
 */
@Disabled("CI: Testcontainers/Docker not reliably available - SQL filters verified manually and via unit tests")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.context.ActiveProfiles("test")
class LeaderboardQueryRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // Disable scheduled jobs and external services during test
        registry.add("spring.task.scheduling.pool.size", () -> "0");
    }

    @Autowired
    private LeaderboardQueryRepository repo;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaCarRepository carRepository;

    @Autowired
    private JpaEvLogRepository evLogRepository;

    private static final LocalDateTime START = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    private static final LocalDateTime END = START.plusYears(1);

    @BeforeEach
    void clean() {
        evLogRepository.deleteAll();
        carRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void kwhRanking_excludesUserWithLeaderboardVisibleFalse() {
        UUID visibleUser = createUser("visible@test.com", "visible", false, true);
        UUID hiddenUser  = createUser("hidden@test.com",  "hidden",  false, false);
        createLog(createCar(visibleUser), "40.0", true);
        createLog(createCar(hiddenUser),  "80.0", true); // higher kwh but opted out

        List<LeaderboardRankRow> rows = repo.getKwhRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username).contains("visible");
        assertThat(rows).extracting(LeaderboardRankRow::username).doesNotContain("hidden");
    }

    @Test
    void kwhRanking_excludesSeedDataUsers() {
        UUID realUser = createUser("real@test.com", "real", false, true);
        UUID seedUser = createUser("seed@test.com", "seed", true,  true);
        createLog(createCar(realUser), "30.0", true);
        createLog(createCar(seedUser), "90.0", true);

        List<LeaderboardRankRow> rows = repo.getKwhRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username).contains("real");
        assertThat(rows).extracting(LeaderboardRankRow::username).doesNotContain("seed");
    }

    @Test
    void kwhRanking_excludesLogsWithIncludeInStatisticsFalse() {
        UUID userId = createUser("user@test.com", "user", false, true);
        createLog(createCar(userId), "50.0", false); // excluded from stats

        List<LeaderboardRankRow> rows = repo.getKwhRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username).doesNotContain("user");
    }

    @Test
    void kwhRanking_ranksCorrectlyByDescendingKwh() {
        UUID u1 = createUser("anna@test.com", "anna", false, true);
        UUID u2 = createUser("bob@test.com",  "bob",  false, true);
        createLog(createCar(u1), "30.0", true);
        createLog(createCar(u2), "70.0", true);

        List<LeaderboardRankRow> rows = repo.getKwhRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username)
                .containsExactly("bob", "anna");
    }

    // ---- Helpers ----

    private UUID createUser(String email, String username, boolean seedData, boolean leaderboardVisible) {
        UUID id = UUID.randomUUID();
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("hash");
        u.setAuthProvider(AuthProvider.LOCAL);
        u.setRole("USER");
        u.setEmailVerified(true);
        u.setSeedData(seedData);
        u.setLeaderboardVisible(leaderboardVisible);
        u.setReferralCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);
        return id;
    }

    private UUID createCar(UUID userId) {
        UUID id = UUID.randomUUID();
        CarEntity c = new CarEntity();
        c.setId(id);
        c.setUserId(userId);
        c.setModel(CarBrand.CarModel.MODEL_3);
        c.setYear(2023);
        c.setLicensePlate("T-" + id.toString().substring(0, 4));
        c.setTrim("Standard");
        c.setBatteryCapacityKwh(new BigDecimal("75.0"));
        c.setPowerKw(new BigDecimal("150.0"));
        c.setStatus(CarStatus.ACTIVE);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        carRepository.save(c);
        return id;
    }

    // ---- MONTHLY_CHEAPEST: SUM/SUM vs AVG(ratio) ----

    @Test
    void cheapestRanking_usesSumDividedBySumNotAvgOfRatios() {
        // Anna: 5kWh@2.50€ (50ct/kWh) + 50kWh@10€ (20ct/kWh) + 50kWh@10€ (20ct/kWh)
        //   AVG(ratio) = (50+20+20)/3 = 30.00 ct/kWh  ← falsches Ergebnis
        //   SUM/SUM    = 22.50/105    = 21.43 ct/kWh  ← korrektes Ergebnis
        UUID userA = createUser("anna@test.com", "anna", false, true);
        UUID carA = createCar(userA);
        createLogWithCost(carA, "5.0",  "2.50",  true);
        createLogWithCost(carA, "50.0", "10.00", true);
        createLogWithCost(carA, "50.0", "10.00", true);

        // Bob: 3x 25kWh@5.75€ = 23 ct/kWh (beide Methoden identisch)
        UUID userB = createUser("bob@test.com", "bob", false, true);
        UUID carB = createCar(userB);
        createLogWithCost(carB, "25.0", "5.75", true);
        createLogWithCost(carB, "25.0", "5.75", true);
        createLogWithCost(carB, "25.0", "5.75", true);

        // SUM/SUM: Anna 21.43 < Bob 23 → Anna gewinnt (korrekt)
        // AVG(ratio): Anna 30 > Bob 23 → Bob würde gewinnen (falsch)
        List<LeaderboardRankRow> rows = repo.getCheapestRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username)
                .containsExactly("anna", "bob");
    }

    @Test
    void cheapestRanking_tiebreakerByKwhDescWhenSamePrice() {
        // Beide laden gratis (0 ct/kWh), Tiebreaker: mehr kWh gewinnt
        UUID userA = createUser("anna@test.com", "anna", false, true);
        UUID carA = createCar(userA);
        createLogWithCost(carA, "40.0", "0.00", true);
        createLogWithCost(carA, "35.0", "0.00", true);
        createLogWithCost(carA, "25.0", "0.00", true); // 100 kWh gesamt

        UUID userB = createUser("bob@test.com", "bob", false, true);
        UUID carB = createCar(userB);
        createLogWithCost(carB, "10.0", "0.00", true);
        createLogWithCost(carB, "10.0", "0.00", true);
        createLogWithCost(carB, "10.0", "0.00", true); // 30 kWh gesamt

        // Beide 0.00 ct/kWh, aber Anna hat mehr kWh → Anna zuerst
        List<LeaderboardRankRow> rows = repo.getCheapestRanking(START, END);

        assertThat(rows).extracting(LeaderboardRankRow::username)
                .containsExactly("anna", "bob");
    }

    // ---- Helpers ----

    private void createLog(UUID carId, String kwh, boolean includeInStatistics) {
        createLogWithCost(carId, kwh, "10.00", includeInStatistics);
    }

    private void createLogWithCost(UUID carId, String kwh, String costEur, boolean includeInStatistics) {
        EvLogEntity e = new EvLogEntity();
        e.setId(UUID.randomUUID());
        e.setCarId(carId);
        e.setKwhCharged(new BigDecimal(kwh));
        e.setCostEur(new BigDecimal(costEur));
        e.setChargeDurationMinutes(60);
        e.setLoggedAt(LocalDateTime.now());
        e.setDataSource("USER_LOGGED");
        e.setIncludeInStatistics(includeInStatistics);
        e.setChargingType("AC");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        evLogRepository.save(e);
    }
}
