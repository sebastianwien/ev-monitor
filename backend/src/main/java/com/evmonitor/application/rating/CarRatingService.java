package com.evmonitor.application.rating;

import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.rating.CarExperience;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingSnapshot;
import com.evmonitor.domain.rating.ReviewDraft;
import com.evmonitor.domain.rating.ReviewProof;
import com.evmonitor.domain.rating.ReviewStatus;
import com.evmonitor.infrastructure.persistence.CarExperienceQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fahrerbewertung des eigenen Autos: Bewertbarkeit, Bewerten und Bearbeiten, Erfahrungstext.
 * Jede Methode prüft die Ownership über {@link Car#isOwnedBy}. Siehe docs/features/vehicle-ratings.md.
 */
@Service
@RequiredArgsConstructor
public class CarRatingService {

    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CarRatingRepository ratingRepository;
    private final CarReviewRepository reviewRepository;
    private final CarExperienceQueryRepository experienceQuery;
    private final CoinLogService coinLogService;
    private final EvLogStatisticsService statisticsService;

    @Transactional(readOnly = true)
    public RatingEligibilityResponse eligibility(UUID userId) {
        List<Car> cars = carRepository.findAllByUserId(userId);
        boolean seed = isSeed(userId);
        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, CarExperience> experiences = experienceQuery.findByCarIds(carIds);
        Set<UUID> rated = carIds.isEmpty() ? Set.of() : ratingRepository.findRatedCarIds(carIds);
        List<CarEligibility> result = cars.stream().map(car -> {
            CarExperience exp = experiences.getOrDefault(car.getId(), CarExperience.NONE);
            return new CarEligibility(car.getId(), car.getModel().getBrand().getDisplayString(),
                    car.getModel().getDisplayName(), !seed && exp.qualifiesForRating(), rated.contains(car.getId()),
                    exp.kmDriven(), exp.logCount(), exp.firstLogDate());
        }).toList();
        return new RatingEligibilityResponse(CarExperience.MIN_LOGS, CarExperience.MIN_KM, result);
    }

    @Transactional(readOnly = true)
    public Optional<MyRatingResponse> myRating(UUID userId, UUID carId) {
        requireOwnedCar(carId, userId);
        return ratingRepository.findByCarIdAndVersion(carId, CarRating.FIRST_VERSION).map(r -> new MyRatingResponse(
                r.answers(), r.getKmDriven(), r.getLogCount(), r.getCreatedAt(), r.getUpdatedAt(),
                ReviewStateResponse.of(reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(r.getId()))));
    }

    /**
     * Legt die Bewertung an oder überschreibt die vorhandene. Coins gibt es einmal je Bewertung.
     *
     * @throws ConflictException wenn das Auto (noch) nicht bewertbar ist
     */
    @Transactional
    public RatingResultResponse submit(UUID userId, UUID carId, RatingRequest request) {
        Car car = requireOwnedCar(carId, userId);
        RatingAnswers answers = request.toAnswers();
        CarExperience experience = experienceQuery.findByCarId(carId);
        boolean seed = isSeed(userId);
        if (seed || !experience.qualifiesForRating()) {
            throw new ConflictException("RATING_NOT_ELIGIBLE", "Dieses Auto ist noch nicht bewertbar.");
        }
        RatingSnapshot snapshot = new RatingSnapshot(car.getModel(), car.getVehicleSpecificationId(),
                experience.kmDriven(), experience.logCount(), car.isBusinessCar(), seed);
        CarRating rating = ratingRepository.findByCarIdAndVersion(carId, CarRating.FIRST_VERSION)
                .map(existing -> {
                    existing.update(snapshot, answers);
                    return existing;
                })
                .orElseGet(() -> CarRating.create(carId, userId, snapshot, answers));
        CarRating saved = ratingRepository.save(rating);
        int coins = coinLogService.awardOncePerEntity(userId, CoinLogService.CoinEvent.RATING_SUBMITTED, saved.getId());
        return result(saved, coins);
    }

    /**
     * Neue Fassung des Erfahrungstexts. Eine offene ältere Fassung wird ersetzt, eine freigegebene
     * bleibt öffentlich, bis die neue freigegeben ist. Unveränderter Text erzeugt keine neue Fassung.
     *
     * @throws ConflictException ohne vorhandene Bewertung
     */
    @Transactional
    public ReviewStateResponse submitReview(UUID userId, UUID carId, ReviewRequest request) {
        requireOwnedCar(carId, userId);
        CarRating rating = requireRating(carId);
        ReviewDraft draft = request.toDraft();
        List<CarReview> versions = new ArrayList<>(reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(rating.getId()));
        boolean deleteWithCar = request.deleteWithCar() != null
                ? request.deleteWithCar()
                : versions.stream().anyMatch(CarReview::isDeleteWithCar);
        versions.forEach(v -> v.changeDeleteWithCar(deleteWithCar));

        Optional<CarReview> latestActive = versions.stream().filter(CarReview::isActive)
                .max(Comparator.comparing(CarReview::getCreatedAt));
        if (latestActive.isEmpty() || !latestActive.get().hasSameContent(draft)) {
            versions.stream().filter(v -> v.getStatus() == ReviewStatus.PENDING).forEach(CarReview::supersede);
            versions.add(CarReview.submit(rating.getId(), draft, proofFor(userId, carId), deleteWithCar));
        }
        reviewRepository.saveAll(versions);
        return ReviewStateResponse.of(versions);
    }

    /** Schalter "Text löschen, wenn ich das Auto lösche" - gilt für alle Fassungen. */
    @Transactional
    public ReviewStateResponse updateReviewSettings(UUID userId, UUID carId, boolean deleteWithCar) {
        requireOwnedCar(carId, userId);
        CarRating rating = requireRating(carId);
        List<CarReview> versions = reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(rating.getId());
        if (versions.isEmpty()) {
            throw new ConflictException("REVIEW_REQUIRED", "Zu dieser Bewertung gibt es keinen Text.");
        }
        versions.forEach(v -> v.changeDeleteWithCar(deleteWithCar));
        reviewRepository.saveAll(versions);
        return ReviewStateResponse.of(versions);
    }

    private RatingResultResponse result(CarRating mine, int coins) {
        List<CarRating> ratings = ratingRepository.findAllByCarModelAndSeedFalse(mine.getCarModel());
        ModelRatingSummary summary = RatingAggregator.summarize(ratings,
                reviewRepository.findRatingIdsWithApprovedText(mine.getCarModel()));
        return new RatingResultResponse(summary.count(), summary.rebuy(), summary.categories(), mine.answers(),
                RatingAggregator.standing(mine, ratings), coins);
    }

    private ReviewProof proofFor(UUID userId, UUID carId) {
        CarExperience experience = experienceQuery.findByCarId(carId);
        BigDecimal consumption = statisticsService.getStatistics(carId, userId, null, null, "MONTH")
                .avgConsumptionKwhPer100km();
        return new ReviewProof(experience.kmDriven(), experience.logCount(), consumption);
    }

    private CarRating requireRating(UUID carId) {
        return ratingRepository.findByCarIdAndVersion(carId, CarRating.FIRST_VERSION)
                .orElseThrow(() -> new ConflictException("RATING_REQUIRED", "Erst bewerten, dann Text schreiben."));
    }

    private boolean isSeed(UUID userId) {
        return userRepository.findById(userId).map(User::isSeedData).orElse(false);
    }

    private Car requireOwnedCar(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId).orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.isOwnedBy(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }
        return car;
    }
}
