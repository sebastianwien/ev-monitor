package com.evmonitor.application.rating;

import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mistral-Prüfung eines Erfahrungstexts, nur auf Knopfdruck im Admin-Tab und beliebig oft wiederholbar.
 * Ergebnis plus Modell, Zeitpunkt, Tokens und Kosten landen in {@code car_review.llm_check}, nie im Log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewModerationService {

    private final CarReviewRepository reviewRepository;
    private final CarRatingRepository ratingRepository;
    private final ReviewModerator moderator;
    private final ObjectMapper objectMapper;

    /** @throws ConflictException mit verständlicher Meldung, wenn Mistral nicht antwortet */
    @Transactional
    public void check(UUID reviewId) {
        CarReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> NotFoundException.forEntity("CarReview", reviewId));
        CarRating rating = ratingRepository.findById(review.getCarRatingId())
                .orElseThrow(() -> NotFoundException.forEntity("CarRating", review.getCarRatingId()));
        ReviewModerator.Result result;
        try {
            result = moderator.moderate(new ReviewModerator.Input(rating.modelDisplayName(), review.isReport(),
                    review.getBody(), review.getProofKmDriven(), review.getProofLogCount(), review.getProofConsumption()));
        } catch (VoiceProviderException e) {
            log.warn("[RATING] llm check failed review={} reason={}", reviewId, e.reason());
            throw new ConflictException("MODERATION_UNAVAILABLE",
                    "Mistral hat gerade nicht geantwortet (" + e.reason() + "). Bitte gleich noch einmal versuchen.");
        }
        review.storeLlmCheck(withMeta(result));
        reviewRepository.save(review);
        log.info("[RATING] llm check review={} model={} costUsd={}", reviewId, result.model(), result.costUsd());
    }

    private String withMeta(ReviewModerator.Result result) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(result.json());
            ObjectNode meta = node.putObject("meta");
            meta.put("model", result.model());
            meta.put("checkedAt", LocalDateTime.now().toString());
            if (result.promptTokens() != null) meta.put("promptTokens", result.promptTokens());
            if (result.completionTokens() != null) meta.put("completionTokens", result.completionTokens());
            meta.put("costUsd", result.costUsd());
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Moderationsergebnis nicht speicherbar", e);
        }
    }
}
