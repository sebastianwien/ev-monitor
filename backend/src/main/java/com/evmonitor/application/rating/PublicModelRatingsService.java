package com.evmonitor.application.rating;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fahrerbewertungen für die öffentliche Modellseite. Ohne Seed, anonymisierte Bewertungen zählen mit,
 * Texte nur freigegeben und nur als {@code publishedBody}. Siehe docs/features/vehicle-ratings.md.
 */
@Service
@RequiredArgsConstructor
public class PublicModelRatingsService {

    public static final String CACHE = "modelRatings";
    static final int MAX_QUOTES = 3;
    static final int MAX_REPORTS = 10;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CarRatingRepository ratingRepository;
    private final CarReviewRepository reviewRepository;

    /** @return leer, wenn es das Modell nicht gibt */
    @Cacheable(value = CACHE, key = "#brandName.toLowerCase() + '-' + #modelName.replace(' ', '_').toLowerCase()")
    @Transactional(readOnly = true)
    public Optional<PublicModelRatingsResponse> forSlug(String brandName, String modelName) {
        return CarBrand.CarModel.findBySlug(brandName, modelName).map(this::forModel);
    }

    private PublicModelRatingsResponse forModel(CarBrand.CarModel model) {
        List<CarRating> ratings = ratingRepository.findAllByCarModelAndSeedFalse(model);
        Map<UUID, CarRating> byId = ratings.stream().collect(Collectors.toMap(CarRating::getId, Function.identity()));
        List<CarReview> approved = reviewRepository.findApprovedByModel(model).stream()
                .filter(v -> byId.containsKey(v.getCarRatingId()) && v.getPublishedBody() != null)
                .toList();
        Set<UUID> withText = approved.stream().map(CarReview::getCarRatingId).collect(Collectors.toSet());

        ModelRatingSummary summary = RatingAggregator.summarize(ratings, withText);
        boolean enough = ratings.size() >= RatingAggregator.MIN_FOR_AVERAGE;
        return new PublicModelRatingsResponse(
                summary.count(),
                summary.rebuy(),
                RatingAggregator.topChips(ratings, CarRating::getRebuyReasons),
                summary.categories(),
                enough ? RatingAggregator.totalCost(ratings) : null,
                enough ? RatingAggregator.topChips(ratings, CarRating::getStrengths) : List.of(),
                enough ? RatingAggregator.topChips(ratings, CarRating::getWeaknesses) : List.of(),
                enough ? List.of() : ratings.stream().map(PublicModelRatingsService::single).toList(),
                approved.stream().filter(v -> !isPublishedAsReport(v)).limit(MAX_QUOTES)
                        .map(v -> quote(v, byId.get(v.getCarRatingId()))).toList(),
                approved.stream().filter(PublicModelRatingsService::isPublishedAsReport).limit(MAX_REPORTS)
                        .map(v -> report(v, byId.get(v.getCarRatingId()))).toList(),
                enough ? new PublicModelRatingsResponse.Aggregate(
                        RatingAggregator.aggregateScore(ratings, withText), ratings.size()) : null);
    }

    /** Zitat oder Bericht entscheidet die veröffentlichte Fassung, nicht der Rohtext. */
    private static boolean isPublishedAsReport(CarReview review) {
        return review.getPublishedBody().length() >= CarReview.REPORT_MIN_CHARS;
    }

    private static PublicModelRatingsResponse.SingleRating single(CarRating r) {
        return new PublicModelRatingsResponse.SingleRating(r.getRebuy(), r.getDailyFit(), r.getLongDistance(),
                r.getSoftware(), r.getBuildQuality(), r.getService(), r.getTotalCost(),
                RatingAggregator.publicKm(r.getKmDriven()), r.isBusinessCar());
    }

    private static PublicModelRatingsResponse.Quote quote(CarReview v, CarRating r) {
        return new PublicModelRatingsResponse.Quote(v.getPublishedBody(), RatingAggregator.publicKm(km(v, r)), month(v));
    }

    private static PublicModelRatingsResponse.Report report(CarReview v, CarRating r) {
        LocalDateTime published = v.getReviewedAt() != null ? v.getReviewedAt() : v.getCreatedAt();
        return new PublicModelRatingsResponse.Report(v.getPublishedBody(), v.getPros(), v.getCons(),
                RatingAggregator.publicKm(km(v, r)), month(v), v.getProofLogCount(), v.getProofConsumption(),
                BigDecimal.valueOf(r.overallScore()).setScale(1, RoundingMode.HALF_UP),
                published.toLocalDate());
    }

    /** Kilometer zum Zeitpunkt des Texts, sonst die der Bewertung. */
    private static int km(CarReview v, CarRating r) {
        return v.getProofKmDriven() != null ? v.getProofKmDriven() : r.getKmDriven();
    }

    private static String month(CarReview v) {
        return (v.getReviewedAt() != null ? v.getReviewedAt() : v.getCreatedAt()).format(MONTH);
    }
}
