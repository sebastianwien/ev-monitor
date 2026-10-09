package com.evmonitor.application.rating;

import com.evmonitor.application.CarService;
import com.evmonitor.application.user.AccountAnonymizationService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.evmonitor.domain.rating.RatingChip;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.RebuyReason;
import com.evmonitor.domain.rating.ReviewStatus;
import com.evmonitor.domain.rating.TotalCostVerdict;
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
 * Fahrerbewertung gegen das echte Schema: Bewertbarkeit, Ownership, Update statt Duplikat,
 * Coins, Statuswechsel der Texte und was bei Auto- und Kontolöschung übrig bleibt.
 */
class CarRatingServicePostgresIT extends AbstractPostgresIT {

    @Autowired CarRatingService ratingService;
    @Autowired CarService carService;
    @Autowired AccountAnonymizationService anonymizationService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired CarRatingRepository ratingRepository;
    @Autowired CarReviewRepository reviewRepository;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        userId = newUser(false);
        carId = newCar(userId);
    }

    // ── Bewertbarkeit ──────────────────────────────────────────────────────

    @Test
    void eligibility_needsFiveLogs_whenNoKmAreKnown() {
        addLogs(carId, 4, null, 0);
        assertThat(eligibilityOf(carId).eligible()).isFalse();
        assertThat(eligibilityOf(carId).logCount()).isEqualTo(4);

        addLogs(carId, 1, null, 0);
        assertThat(eligibilityOf(carId).eligible()).isTrue();
    }

    @Test
    void eligibility_needs300Km_whenFewerLogs() {
        addLogs(carId, 1, 10_000, 0);
        addLogs(carId, 1, 10_299, 0);
        assertThat(eligibilityOf(carId).kmDriven()).isEqualTo(299);
        assertThat(eligibilityOf(carId).eligible()).isFalse();

        addLogs(carId, 1, 10_300, 0);
        assertThat(eligibilityOf(carId).kmDriven()).isEqualTo(300);
        assertThat(eligibilityOf(carId).eligible()).isTrue();
    }

    @Test
    void eligibility_ignoresDeletedLogs() {
        addLogs(carId, 4, null, 0);
        addLogs(carId, 1, null, 1);
        assertThat(eligibilityOf(carId).logCount()).isEqualTo(4);
        assertThat(eligibilityOf(carId).eligible()).isFalse();
    }

    @Test
    void eligibility_isAlwaysFalseForSeedUsers() {
        UUID seedUser = newUser(true);
        UUID seedCar = newCar(seedUser);
        addLogs(seedCar, 10, null, 0);

        RatingEligibilityResponse response = ratingService.eligibility(seedUser);

        assertThat(response.cars()).singleElement().satisfies(c -> assertThat(c.eligible()).isFalse());
    }

    @Test
    void eligibility_marksRatedCars() {
        addLogs(carId, 5, null, 0);
        assertThat(eligibilityOf(carId).rated()).isFalse();

        ratingService.submit(userId, carId, validRequest());

        assertThat(eligibilityOf(carId).rated()).isTrue();
    }

    // ── Bewerten ───────────────────────────────────────────────────────────

    @Test
    void submit_foreignCar_isForbidden() {
        addLogs(carId, 5, null, 0);
        UUID stranger = newUser(false);

        assertThatThrownBy(() -> ratingService.submit(stranger, carId, validRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submit_notEligible_isConflict() {
        addLogs(carId, 2, null, 0);

        assertThatThrownBy(() -> ratingService.submit(userId, carId, validRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submit_snapshotsCarAndUser() {
        addLogs(carId, 1, 20_000, 0);
        addLogs(carId, 4, 21_500, 0);

        ratingService.submit(userId, carId, validRequest());

        CarRating rating = ratingRepository.findByCarIdAndVersion(carId, CarRating.FIRST_VERSION).orElseThrow();
        assertThat(rating.getCarModel()).isEqualTo(CarBrand.CarModel.ID_3);
        assertThat(rating.getKmDriven()).isEqualTo(1_500);
        assertThat(rating.getLogCount()).isEqualTo(5);
        assertThat(rating.isSeed()).isFalse();
        assertThat(rating.getUserId()).isEqualTo(userId);
    }

    @Test
    void submit_twice_updatesInsteadOfDuplicating_andPaysCoinsOnce() {
        addLogs(carId, 5, null, 0);

        RatingResultResponse first = ratingService.submit(userId, carId, validRequest());
        RatingRequest changed = new RatingRequest(RebuyAnswer.NO, List.of(), 2, null, 2, 2, null,
                TotalCostVerdict.PRICIER, List.of(), List.of(RatingChip.SOFTWARE));
        RatingResultResponse second = ratingService.submit(userId, carId, changed);

        assertThat(first.coinsAwarded()).isEqualTo(50);
        assertThat(second.coinsAwarded()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM car_rating WHERE car_id = ?", Long.class, carId)).isEqualTo(1);
        CarRating rating = ratingRepository.findByCarIdAndVersion(carId, CarRating.FIRST_VERSION).orElseThrow();
        assertThat(rating.getRebuy()).isEqualTo(RebuyAnswer.NO);
        assertThat(rating.getLongDistance()).isNull();
        assertThat(rating.getWeaknesses()).containsExactly("SOFTWARE");
    }

    @Test
    void submit_rebuyReasonsOnlyWithBrandOtherModel() {
        addLogs(carId, 5, null, 0);
        RatingRequest invalid = new RatingRequest(RebuyAnswer.YES, List.of(RebuyReason.SIZE), 4, 4, 4, 4, 4,
                TotalCostVerdict.EXPECTED, List.of(), List.of());

        assertThatThrownBy(() -> ratingService.submit(userId, carId, invalid))
                .isInstanceOf(ValidationException.class);

        RatingRequest valid = new RatingRequest(RebuyAnswer.BRAND_OTHER_MODEL, List.of(RebuyReason.SIZE), 4, 4, 4, 4, 4,
                TotalCostVerdict.EXPECTED, List.of(), List.of());
        ratingService.submit(userId, carId, valid);
        assertThat(ratingRepository.findByCarIdAndVersion(carId, 1).orElseThrow().getRebuyReasons()).containsExactly("SIZE");
    }

    @Test
    void submit_moreThanThreeChips_isRejected() {
        addLogs(carId, 5, null, 0);
        RatingRequest tooMany = new RatingRequest(RebuyAnswer.YES, List.of(), 4, 4, 4, 4, 4, TotalCostVerdict.EXPECTED,
                List.of(RatingChip.SPACE, RatingChip.DESIGN, RatingChip.PRICE, RatingChip.SOFTWARE), List.of());

        assertThatThrownBy(() -> ratingService.submit(userId, carId, tooMany))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void submit_returnsOwnResultWithCommunityCount() {
        addLogs(carId, 5, null, 0);

        RatingResultResponse result = ratingService.submit(userId, carId, validRequest());

        assertThat(result.count()).isGreaterThanOrEqualTo(1);
        assertThat(result.rebuy().yes()).isGreaterThanOrEqualTo(1);
        assertThat(result.mine().dailyFit()).isEqualTo(4);
    }

    // ── Erfahrungstext ─────────────────────────────────────────────────────

    @Test
    void submitReview_withoutRating_isConflict() {
        addLogs(carId, 5, null, 0);

        assertThatThrownBy(() -> ratingService.submitReview(userId, carId, review("Läuft.")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitReview_foreignCar_isForbidden() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());

        assertThatThrownBy(() -> ratingService.submitReview(newUser(false), carId, review("Fremd.")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submitReview_blankOrTooLong_isRejected() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());

        assertThatThrownBy(() -> ratingService.submitReview(userId, carId, review("   ")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ratingService.submitReview(userId, carId, review("x".repeat(5_001))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void submitReview_newVersionSupersedesOpenOne_andFreezesProof() {
        addLogs(carId, 1, 30_000, 0);
        addLogs(carId, 4, 31_000, 0);
        ratingService.submit(userId, carId, validRequest());

        ReviewStateResponse first = ratingService.submitReview(userId, carId, review("  Erste Fassung.  "));
        ReviewStateResponse second = ratingService.submitReview(userId, carId, review("Zweite Fassung."));

        assertThat(first.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(second.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(second.body()).isEqualTo("Zweite Fassung.");
        List<CarReview> reviews = reviewsOf(carId);
        assertThat(reviews).extracting(CarReview::getStatus)
                .containsExactlyInAnyOrder(ReviewStatus.SUPERSEDED, ReviewStatus.PENDING);
        CarReview pending = reviews.stream().filter(r -> r.getStatus() == ReviewStatus.PENDING).findFirst().orElseThrow();
        assertThat(pending.getBody()).isEqualTo("Zweite Fassung.");
        assertThat(pending.getProofKmDriven()).isEqualTo(1_000);
        assertThat(pending.getProofLogCount()).isEqualTo(5);
    }

    @Test
    void submitReview_keepsApprovedVersionVisible() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submitReview(userId, carId, review("Freigegeben."));
        jdbc.update("UPDATE car_review SET status = 'APPROVED', published_body = body WHERE car_rating_id = ?", ratingIdOf(carId));

        ReviewStateResponse state = ratingService.submitReview(userId, carId, review("Neue Fassung."));

        assertThat(state.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(state.published()).isTrue();
        assertThat(reviewsOf(carId)).extracting(CarReview::getStatus)
                .containsExactlyInAnyOrder(ReviewStatus.APPROVED, ReviewStatus.PENDING);
    }

    @Test
    void submitReview_sameTextAgain_createsNoNewVersion() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submitReview(userId, carId, review("Gleich."));

        ratingService.submitReview(userId, carId, review("Gleich."));

        assertThat(reviewsOf(carId)).hasSize(1);
    }

    @Test
    void myRating_showsOwnAnswersAndReviewState() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submitReview(userId, carId, review("Mein Text."));

        MyRatingResponse mine = ratingService.myRating(userId, carId).orElseThrow();

        assertThat(mine.answers().dailyFit()).isEqualTo(4);
        assertThat(mine.review().body()).isEqualTo("Mein Text.");
        assertThat(mine.review().status()).isEqualTo(ReviewStatus.PENDING);
        assertThatThrownBy(() -> ratingService.myRating(newUser(false), carId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reviewSettings_applyToAllVersions() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submitReview(userId, carId, review("Eins."));
        ratingService.submitReview(userId, carId, review("Zwei."));

        ReviewStateResponse state = ratingService.updateReviewSettings(userId, carId, true);

        assertThat(state.deleteWithCar()).isTrue();
        assertThat(reviewsOf(carId)).allMatch(CarReview::isDeleteWithCar);
    }

    // ── Löschen ────────────────────────────────────────────────────────────

    @Test
    void purge_keepsRatingAnonymously_andDropsTextOnlyWhenRequested() {
        UUID otherCar = newCar(userId);
        addLogs(carId, 5, null, 0);
        addLogs(otherCar, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submit(userId, otherCar, validRequest());
        ratingService.submitReview(userId, carId, review("Bleibt."));
        ratingService.submitReview(userId, otherCar, review("Geht mit dem Auto."));
        ratingService.updateReviewSettings(userId, otherCar, true);
        UUID keptRating = ratingIdOf(carId);
        UUID droppedTextRating = ratingIdOf(otherCar);

        expireDeleted(carId);
        expireDeleted(otherCar);
        carService.purgeExpiredDeletedCars();

        CarRating kept = ratingRepository.findById(keptRating).orElseThrow();
        assertThat(kept.getCarId()).isNull();
        assertThat(kept.getAnonymizedAt()).isNotNull();
        assertThat(reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(keptRating)).hasSize(1);
        assertThat(ratingRepository.findById(droppedTextRating)).isPresent();
        assertThat(reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(droppedTextRating)).isEmpty();
    }

    @Test
    void accountDeletion_anonymizesRating_andDeletesAllTexts() {
        addLogs(carId, 5, null, 0);
        ratingService.submit(userId, carId, validRequest());
        ratingService.submitReview(userId, carId, review("Weg damit."));
        UUID ratingId = ratingIdOf(carId);

        anonymizationService.anonymizeCarsOf(userId);

        CarRating rating = ratingRepository.findById(ratingId).orElseThrow();
        assertThat(rating.getUserId()).isNull();
        assertThat(rating.getAnonymizedAt()).isNotNull();
        assertThat(rating.getCarId()).isEqualTo(carId);
        assertThat(reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(ratingId)).isEmpty();
    }

    // ── Helfer ─────────────────────────────────────────────────────────────

    private static RatingRequest validRequest() {
        return new RatingRequest(RebuyAnswer.YES, List.of(), 4, 3, 4, 5, null, TotalCostVerdict.CHEAPER,
                List.of(RatingChip.EFFICIENCY, RatingChip.SPACE), List.of(RatingChip.SOFTWARE));
    }

    private static ReviewRequest review(String body) {
        return new ReviewRequest(body, List.of("Effizient"), List.of("App"), null);
    }

    private UUID newUser(boolean seed) {
        User user = TestDataBuilder.createTestUser("rating-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de")
                .toBuilder().seedData(seed).build();
        return userRepository.save(user).getId();
    }

    private UUID newCar(UUID owner) {
        Car car = carRepository.save(TestDataBuilder.createTestCar(owner, CarBrand.CarModel.ID_3, new BigDecimal("58")));
        return car.getId();
    }

    private int logOffset = 0;

    /** Legt {@code n} Logs mit eindeutigem Zeitpunkt an, optional mit km-Stand und als gelöscht. */
    private void addLogs(UUID car, int n, Integer odometerKm, int deleted) {
        for (int i = 0; i < n; i++) {
            logOffset++;
            jdbc.update("INSERT INTO ev_log (id, car_id, kwh_charged, logged_at, odometer_km, data_source, created_at, updated_at, deleted_at) "
                            + "VALUES (?, ?, 30.0, now() - (? * interval '1 hour'), ?, 'USER_LOGGED', now(), now(), "
                            + (deleted > 0 ? "now()" : "NULL") + ")",
                    UUID.randomUUID(), car, logOffset, odometerKm);
        }
    }

    private CarEligibility eligibilityOf(UUID car) {
        return ratingService.eligibility(userId).cars().stream()
                .filter(c -> c.carId().equals(car)).findFirst().orElseThrow();
    }

    private UUID ratingIdOf(UUID car) {
        return ratingRepository.findByCarIdAndVersion(car, CarRating.FIRST_VERSION).orElseThrow().getId();
    }

    private List<CarReview> reviewsOf(UUID car) {
        return reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(ratingIdOf(car));
    }

    private void expireDeleted(UUID car) {
        carService.deleteCar(car, userId);
        jdbc.update("UPDATE car SET deleted_at = now() - interval '30 days' WHERE id = ?", car);
    }
}
