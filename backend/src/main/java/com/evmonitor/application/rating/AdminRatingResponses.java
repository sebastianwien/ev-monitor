package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.ReviewStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs des Admin-Tabs "Bewertungen". Keine E-Mail, kein Kennzeichen, Nutzer nur als gekürzte ID. */
public final class AdminRatingResponses {

    private AdminRatingResponses() {
    }

    /** STARS_ONLY = ohne Text, SHORT = Zitat unter 600 Zeichen, LONG = Erfahrungsbericht. */
    public enum TextType { STARS_ONLY, SHORT, LONG }

    public record Page(List<ListItem> items, int page, int size, long total, long openTexts) {
    }

    public record ListItem(
            UUID ratingId,
            String model,
            String rebuy,
            BigDecimal overall,
            int kmDriven,
            int logCount,
            LocalDateTime updatedAt,
            TextType type,
            UUID reviewId,
            ReviewStatus reviewStatus,
            String preview,
            LocalDateTime reviewSubmittedAt,
            String userShort) {
    }

    public record Detail(
            UUID ratingId,
            String model,
            RatingAnswers answers,
            int kmDriven,
            int logCount,
            boolean businessCar,
            boolean anonymized,
            String userShort,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<Version> versions) {
    }

    public record Version(
            UUID reviewId,
            String body,
            List<String> pros,
            List<String> cons,
            ReviewStatus status,
            String publishedBody,
            String rejectionReason,
            String rejectionNote,
            @JsonRawValue String llmCheck,
            boolean deleteWithCar,
            Integer proofKmDriven,
            Integer proofLogCount,
            BigDecimal proofConsumption,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {
    }
}
