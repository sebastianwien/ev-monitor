package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findDormantAutoSyncUsersDue targets users whose car is still logging via a live connector
 * (TESLA_LIVE/SMARTCAR_LIVE/VWGROUP_LIVE/XPENG_LIVE) but who themselves haven't opened the app
 * in a while - the ev_log-based re-engagement query never fires for them because the connector
 * keeps writing fresh logs regardless of human activity.
 *
 * <p>Uses {@code last_seen <= day} plus a "not yet mailed" flag rather than an exact-day match,
 * so the same query also absorbs any backlog of users already further gone than the threshold.
 *
 * <p>{@code @Transactional}: batchUpdateLastSeen/markDormantAutoSyncEmailSent are
 * {@code @Modifying} queries and need an active transaction; this also gives each test
 * automatic rollback.
 */
@Transactional
class DormantAutoSyncQueryTest extends AbstractIntegrationTest {

    @Autowired
    private JpaUserRepository jpaUserRepository;

    private static final LocalDate TARGET_DAY = LocalDate.of(2024, 3, 15);

    @Test
    void userLastSeenExactlyOnCutoffWithRecentAutoSyncLog_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY);
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.TESLA_LIVE, LocalDateTime.now().minusDays(2));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userLastSeenLongBeforeCutoff_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY.minusDays(90));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.TESLA_LIVE, LocalDateTime.now().minusDays(2));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithoutAutoSyncSource_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY);
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.USER_LOGGED, LocalDateTime.now().minusDays(2));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userWithStaleAutoSyncLog_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY);
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        // Connector hasn't sent anything in a while either - a stale connection is not
        // "still auto-logging", it belongs to the plain re-engagement flow instead.
        saveAutoSyncLog(car.getId(), DataSource.TESLA_LIVE, LocalDateTime.now().minusDays(30));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userLastSeenAfterCutoff_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY.plusDays(1));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.SMARTCAR_LIVE, LocalDateTime.now().minusDays(2));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userWithNoLastSeen_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.VWGROUP_LIVE, LocalDateTime.now().minusDays(2));

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userAlreadyMarkedSent_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        setLastSeen(user.getId(), TARGET_DAY.minusDays(90));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75)));
        saveAutoSyncLog(car.getId(), DataSource.TESLA_LIVE, LocalDateTime.now().minusDays(2));

        userRepository.markDormantAutoSyncEmailSent(user.getId(), LocalDateTime.now());

        List<User> result = userRepository.findDormantAutoSyncUsersDue(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    // --- helpers ---

    private void setLastSeen(UUID userId, LocalDate day) {
        jpaUserRepository.batchUpdateLastSeen(List.of(userId), day.atTime(12, 0));
    }

    private void saveAutoSyncLog(UUID carId, DataSource dataSource, LocalDateTime loggedAt) {
        LocalDateTime now = LocalDateTime.now();
        evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(BigDecimal.valueOf(30))
                .chargeDurationMinutes(60)
                .geohash("u33dc")
                .odometerKm(50000)
                .socAfterChargePercent(BigDecimal.valueOf(80))
                .loggedAt(loggedAt)
                .dataSource(dataSource)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String uniqueEmail() {
        return "dormant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
