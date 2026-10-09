package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.RatingAnswers;

/**
 * Persönliches Ergebnis nach dem Absenden: eigene Antworten im Vergleich zu allen Fahrern des Modells.
 * {@code categories} erst ab 5 Bewertungen, {@code standing} erst ab 15.
 */
public record RatingResultResponse(
        int count,
        ModelRatingSummary.RebuyCounts rebuy,
        ModelRatingSummary.Categories categories,
        RatingAnswers mine,
        RatingAggregator.Standing standing,
        int coinsAwarded) {
}
