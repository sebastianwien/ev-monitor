package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.RatingAnswers;

import java.time.LocalDateTime;

/** GET /api/cars/{carId}/rating: eigene Antworten zum Bearbeiten plus Stand des Texts (null ohne Text). */
public record MyRatingResponse(
        RatingAnswers answers,
        int kmDriven,
        int logCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ReviewStateResponse review) {
}
