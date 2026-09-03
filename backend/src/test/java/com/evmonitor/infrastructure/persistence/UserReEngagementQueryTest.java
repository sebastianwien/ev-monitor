package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that findUsersDueForReEngagement correctly considers both ev_log AND ev_trip as user
 * activity indicators.
 *
 * <p>Uses {@code last_log <= day} plus a "not yet mailed" flag rather than an exact-day match,
 * so the same query also absorbs any backlog of users already further gone than the threshold -
 * see {@link DormantAutoSyncQueryTest} for the sibling case this pattern was copied from.
 *
 * <p>{@code @Transactional}: markReEngagementEmailSent is a {@code @Modifying} query and needs
 * an active transaction; this also gives each test automatic rollback.
 */
@Transactional
class UserReEngagementQueryTest extends AbstractIntegrationTest {

    @Autowired
    private EvTripRepository evTripRepository;

    private static final LocalDate TARGET_DAY = LocalDate.of(2024, 3, 15);

    @Test
    void userWithOnlyLogOnTargetDay_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        saveEvLog(car.getId(), TARGET_DAY);

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithOnlyTripOnTargetDay_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        saveEvTrip(user.getId(), car.getId(), TARGET_DAY);

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithLogBeforeAndTripOnTargetDay_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        // last log was 5 days earlier
        saveEvLog(car.getId(), TARGET_DAY.minusDays(5));
        // but trip was on target day
        saveEvTrip(user.getId(), car.getId(), TARGET_DAY);

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithTripBeforeAndLogOnTargetDay_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        // trip was 5 days earlier
        saveEvTrip(user.getId(), car.getId(), TARGET_DAY.minusDays(5));
        // but log was on target day
        saveEvLog(car.getId(), TARGET_DAY);

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithActivityLongBeforeTargetDay_isIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        // Already inactive well before the threshold - the backlog case <= is meant to catch.
        saveEvLog(car.getId(), TARGET_DAY.minusDays(90));

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).contains(user.getId());
    }

    @Test
    void userWithLatestActivityAfterTargetDay_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        saveEvLog(car.getId(), TARGET_DAY);
        // newer activity after target day - should not be a re-engagement candidate
        saveEvTrip(user.getId(), car.getId(), TARGET_DAY.plusDays(2));

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userWithNoActivity_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    void userAlreadyMarkedSent_isNotIncluded() {
        User user = createAndSaveUser(uniqueEmail());
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, java.math.BigDecimal.valueOf(75)));
        saveEvLog(car.getId(), TARGET_DAY.minusDays(90));

        userRepository.markReEngagementEmailSent(user.getId(), LocalDateTime.now());

        List<User> result = userRepository.findUsersDueForReEngagement(TARGET_DAY);

        assertThat(result).extracting(User::getId).doesNotContain(user.getId());
    }

    // --- helpers ---

    private void saveEvLog(UUID carId, LocalDate date) {
        evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                carId,
                java.math.BigDecimal.valueOf(30),
                java.math.BigDecimal.valueOf(8),
                date.atTime(12, 0)
        ));
    }

    private void saveEvTrip(UUID userId, UUID carId, LocalDate date) {
        OffsetDateTime ended = date.atTime(14, 0).atOffset(ZoneOffset.UTC);
        evTripRepository.save(EvTrip.builder()
                .userId(userId)
                .carId(carId)
                .dataSource(EvTrip.DATA_SOURCE_API_UPLOAD)
                .tripStartedAt(ended.minusHours(1))
                .tripEndedAt(ended)
                .status("COMPLETED")
                .userCreated(true)
                .build());
    }

    private String uniqueEmail() {
        return "re-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
