package com.evmonitor.application.feedback;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.feedback.FeedbackRating;
import com.evmonitor.domain.feedback.FeedbackSubjectType;
import com.evmonitor.domain.feedback.FeedbackTag;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Korrekt erkannt?" gegen das echte Schema: eine Bewertung je Ladevorgang oder Fahrt,
 * Ownership, nur automatisch erfasste Daten, Tags passend zum Gegenstand.
 */
class DataFeedbackServicePostgresIT extends AbstractPostgresIT {

    @Autowired DataFeedbackService feedbackService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private UUID carId;

    @BeforeEach
    void setUp() {
        user = newUser();
        carId = newCar(user.getId());
    }

    @Test
    void positiveRatingOnSyncedLog_isStoredWithDataSourceSnapshot() {
        UUID logId = addLog(carId, "EU_DATA_ACT_SYNC");

        DataFeedbackResponse response = feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null));

        assertThat(response.rating()).isEqualTo(FeedbackRating.POSITIVE);
        assertThat(jdbc.queryForObject("SELECT data_source FROM data_feedback WHERE subject_id = ?", String.class, logId))
                .isEqualTo("EU_DATA_ACT_SYNC");
    }

    @Test
    void resubmit_updatesInsteadOfDuplicating() {
        UUID logId = addLog(carId, "SMARTCAR_LIVE");
        feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null));

        DataFeedbackResponse response = feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.NEGATIVE, List.of(FeedbackTag.KWH_WRONG, FeedbackTag.TIME_WRONG), "Waren 24 kWh"));

        assertThat(response.rating()).isEqualTo(FeedbackRating.NEGATIVE);
        assertThat(response.tags()).containsExactly(FeedbackTag.KWH_WRONG, FeedbackTag.TIME_WRONG);
        assertThat(response.comment()).isEqualTo("Waren 24 kWh");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_feedback WHERE subject_id = ?", Integer.class, logId))
                .isEqualTo(1);
    }

    @Test
    void positiveRating_dropsTagsAndComment() {
        UUID logId = addLog(carId, "TESLA_LIVE");

        DataFeedbackResponse response = feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(FeedbackTag.KWH_WRONG), "egal"));

        assertThat(response.tags()).isEmpty();
        assertThat(response.comment()).isNull();
    }

    @Test
    void foreignLog_isNotFound() {
        UUID otherCar = newCar(newUser().getId());
        UUID logId = addLog(otherCar, "SMARTCAR_LIVE");

        assertThatThrownBy(() -> feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void manuallyEnteredOrUploadedLog_cannotBeRated() {
        UUID manual = addLog(carId, "USER_LOGGED");
        UUID upload = addLog(carId, "EU_DATA_ACT_IMPORT");

        for (UUID logId : List.of(manual, upload)) {
            assertThatThrownBy(() -> feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                    new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void tagOfOtherSubjectType_isRejected() {
        UUID logId = addLog(carId, "SMARTCAR_LIVE");

        assertThatThrownBy(() -> feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.NEGATIVE, List.of(FeedbackTag.DISTANCE_WRONG), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void detectedTrip_canBeRated_userCreatedTripCannot() {
        UUID detected = addTrip(carId, user.getId(), "API_UPLOAD");
        UUID userCreated = addTrip(carId, user.getId(), "USER_CREATED");

        feedbackService.submit(user, FeedbackSubjectType.TRIP, detected,
                new DataFeedbackRequest(FeedbackRating.NEGATIVE, List.of(FeedbackTag.DISTANCE_WRONG), null));

        assertThat(feedbackService.listForUser(user.getId()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.subjectType()).isEqualTo(FeedbackSubjectType.TRIP);
                    assertThat(f.subjectId()).isEqualTo(detected);
                });
        assertThatThrownBy(() -> feedbackService.submit(user, FeedbackSubjectType.TRIP, userCreated,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void foreignTrip_isNotFound() {
        User other = newUser();
        UUID trip = addTrip(newCar(other.getId()), other.getId(), "API_UPLOAD");

        assertThatThrownBy(() -> feedbackService.submit(user, FeedbackSubjectType.TRIP, trip,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void clear_removesOnlyOwnRating() {
        UUID logId = addLog(carId, "SMARTCAR_LIVE");
        feedbackService.submit(user, FeedbackSubjectType.LOG, logId,
                new DataFeedbackRequest(FeedbackRating.POSITIVE, List.of(), null));

        feedbackService.clear(newUser(), FeedbackSubjectType.LOG, logId);
        assertThat(feedbackService.listForUser(user.getId())).hasSize(1);

        feedbackService.clear(user, FeedbackSubjectType.LOG, logId);
        assertThat(feedbackService.listForUser(user.getId())).isEmpty();
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    private User newUser() {
        return userRepository.save(TestDataBuilder.createTestUser("fb-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
    }

    private UUID newCar(UUID owner) {
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner, CarBrand.CarModel.ID_3, new BigDecimal("58")));
        return car.getId();
    }

    private UUID addLog(UUID car, String dataSource) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ev_log (id, car_id, kwh_charged, logged_at, data_source, created_at, updated_at) "
                + "VALUES (?, ?, 30.0, now(), ?, now(), now())", id, car, dataSource);
        return id;
    }

    private UUID addTrip(UUID car, UUID owner, String dataSource) {
        return jdbc.queryForObject("INSERT INTO ev_trip (car_id, user_id, data_source, trip_started_at, distance_km) "
                + "VALUES (?, ?, ?, now(), 42.0) RETURNING id", UUID.class, car, owner, dataSource);
    }
}
