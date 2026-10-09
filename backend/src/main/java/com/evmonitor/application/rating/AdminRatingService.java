package com.evmonitor.application.rating;

import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.rating.AdminRatingResponses.Detail;
import com.evmonitor.application.rating.AdminRatingResponses.ListItem;
import com.evmonitor.application.rating.AdminRatingResponses.Page;
import com.evmonitor.application.rating.AdminRatingResponses.TextType;
import com.evmonitor.application.rating.AdminRatingResponses.Version;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.evmonitor.domain.rating.RejectionReason;
import com.evmonitor.domain.rating.ReviewStatus;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.email.ReviewRejectedMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Moderation der Fahrerbewertungen im Admin-Tab. Freigabe immer durch einen Menschen, Mistral hilft
 * nur auf Knopfdruck. Texte und Begründungen landen nie im Log, nur IDs und Entscheidungen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRatingService {

    static final int PREVIEW_CHARS = 160;
    static final int MAX_PAGE_SIZE = 100;

    private final CarRatingRepository ratingRepository;
    private final CarReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CoinLogService coinLogService;
    private final EmailService emailService;
    private final CacheManager cacheManager;

    /** Filter der Liste, alle optional. {@code status} bezieht sich auf die jüngste Textfassung. */
    public record Filter(ReviewStatus status, TextType type, CarBrand.CarModel model, int page, int size) {
    }

    /** Offene Texte zuerst, danach die zuletzt geänderten. */
    @Transactional(readOnly = true)
    public Page list(Filter filter) {
        List<CarRating> ratings = filter.model() == null
                ? ratingRepository.findAll()
                : ratingRepository.findAll().stream().filter(r -> r.getCarModel() == filter.model()).toList();
        Map<UUID, List<CarReview>> reviews = reviewRepository.findAll().stream()
                .collect(Collectors.groupingBy(CarReview::getCarRatingId));
        List<ListItem> items = ratings.stream()
                .map(r -> item(r, latest(reviews.getOrDefault(r.getId(), List.of()))))
                .filter(i -> filter.status() == null || filter.status() == i.reviewStatus())
                .filter(i -> filter.type() == null || filter.type() == i.type())
                .sorted(Comparator.comparing((ListItem i) -> i.reviewStatus() != ReviewStatus.PENDING)
                        .thenComparing(ListItem::updatedAt, Comparator.reverseOrder()))
                .toList();
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, filter.size()));
        int page = Math.max(0, filter.page());
        long open = items.stream().filter(i -> i.reviewStatus() == ReviewStatus.PENDING).count();
        return new Page(items.stream().skip((long) page * size).limit(size).toList(), page, size, items.size(), open);
    }

    @Transactional(readOnly = true)
    public long countOpenTexts() {
        return reviewRepository.findAll().stream().filter(v -> v.getStatus() == ReviewStatus.PENDING).count();
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID ratingId) {
        CarRating r = requireRating(ratingId);
        List<Version> versions = reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(ratingId).stream()
                .map(v -> new Version(v.getId(), v.getBody(), v.getPros(), v.getCons(), v.getStatus(), v.getPublishedBody(),
                        v.getRejectionReason(), v.getRejectionNote(), v.getLlmCheck(), v.isDeleteWithCar(),
                        v.getProofKmDriven(), v.getProofLogCount(), v.getProofConsumption(), v.getReviewedAt(), v.getCreatedAt()))
                .toList();
        return new Detail(r.getId(), modelName(r), r.answers(), r.getKmDriven(), r.getLogCount(), r.isBusinessCar(),
                r.getAnonymizedAt() != null, userShort(r.getUserId()), r.getCreatedAt(), r.getUpdatedAt(), versions);
    }

    /**
     * Freigabe, optional mit Schwärzungen. Eine ältere freigegebene Fassung derselben Bewertung wird ersetzt.
     * Coins je nach Länge der veröffentlichten Fassung, einmal je Fassung.
     */
    @Transactional
    public Detail approve(UUID reviewId, String publishedBody, UUID adminId) {
        CarReview review = requireReview(reviewId);
        CarRating rating = requireRating(review.getCarRatingId());
        review.approve(publishedBody, adminId);
        List<CarReview> others = reviewRepository.findAllByCarRatingIdOrderByCreatedAtAsc(rating.getId()).stream()
                .filter(v -> !v.getId().equals(reviewId))
                .toList();
        others.forEach(CarReview::replaceApproved);
        reviewRepository.saveAll(others);
        reviewRepository.save(review);
        if (rating.getUserId() != null) {
            CoinLogService.CoinEvent event = review.getPublishedBody().length() >= CarReview.REPORT_MIN_CHARS
                    ? CoinLogService.CoinEvent.REVIEW_APPROVED_LONG
                    : CoinLogService.CoinEvent.REVIEW_APPROVED_SHORT;
            coinLogService.awardOncePerEntity(rating.getUserId(), event, reviewId);
        }
        clearPublicCache();
        log.info("[RATING] approved review={} rating={} admin={} redacted={}", reviewId, rating.getId(), adminId,
                !review.getPublishedBody().equals(review.getBody()));
        return detail(rating.getId());
    }

    /** Ablehnung mit Grund. Der Fahrer bekommt eine Mail mit Grund und Link zum Bearbeiten. */
    @Transactional
    public Detail reject(UUID reviewId, RejectionReason reason, String note, UUID adminId) {
        CarReview review = requireReview(reviewId);
        CarRating rating = requireRating(review.getCarRatingId());
        review.reject(reason, note, adminId);
        reviewRepository.save(review);
        log.info("[RATING] rejected review={} rating={} admin={} reason={}", reviewId, rating.getId(), adminId, reason);
        notifyRejection(rating, review);
        return detail(rating.getId());
    }

    /** Mail-Fehler kippen die Entscheidung nicht - die Ablehnung ist im Formular ohnehin sichtbar. */
    private void notifyRejection(CarRating rating, CarReview review) {
        if (rating.getUserId() == null) return;
        userRepository.findById(rating.getUserId()).ifPresent(user -> {
            try {
                emailService.sendReviewRejectedEmail(new ReviewRejectedMail(user.getEmail(), user.getUsername(),
                        user.getRegistrationLocale(), modelName(rating), review.getRejectionReason(),
                        review.getRejectionNote(), rating.getCarId()));
            } catch (RuntimeException e) {
                log.warn("[RATING] rejection mail failed review={}: {}", review.getId(), e.getClass().getSimpleName());
            }
        });
    }

    private void clearPublicCache() {
        Optional.ofNullable(cacheManager.getCache(PublicModelRatingsService.CACHE)).ifPresent(Cache::clear);
    }

    private static CarReview latest(List<CarReview> versions) {
        return versions.stream()
                .filter(v -> v.getStatus() != ReviewStatus.SUPERSEDED)
                .max(Comparator.comparing(CarReview::getCreatedAt))
                .orElse(null);
    }

    private static ListItem item(CarRating r, CarReview latest) {
        TextType type = latest == null ? TextType.STARS_ONLY : latest.isReport() ? TextType.LONG : TextType.SHORT;
        String preview = latest == null ? null
                : latest.getBody().length() <= PREVIEW_CHARS ? latest.getBody() : latest.getBody().substring(0, PREVIEW_CHARS) + " …";
        return new ListItem(r.getId(), modelName(r), r.getRebuy().name(),
                BigDecimal.valueOf(r.overallScore()).setScale(1, RoundingMode.HALF_UP), r.getKmDriven(), r.getLogCount(),
                r.getUpdatedAt(), type, latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus(),
                preview, latest == null ? null : latest.getCreatedAt(), userShort(r.getUserId()));
    }

    private static String modelName(CarRating r) {
        return r.getCarModel().getBrand().getDisplayString() + " " + r.getCarModel().getDisplayName();
    }

    private static String userShort(UUID userId) {
        return userId == null ? null : userId.toString().substring(0, 8);
    }

    private CarRating requireRating(UUID ratingId) {
        return ratingRepository.findById(ratingId).orElseThrow(() -> NotFoundException.forEntity("CarRating", ratingId));
    }

    private CarReview requireReview(UUID reviewId) {
        return reviewRepository.findById(reviewId).orElseThrow(() -> NotFoundException.forEntity("CarReview", reviewId));
    }
}
