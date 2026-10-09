package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.TotalCostVerdict;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/public/models/{brand}/{model}/ratings. Ohne IDs und ohne Nutzerbezug, öffentlich nur
 * absolute Zahlen und auf 5.000 gerundete Kilometer. Unter 5 Bewertungen Einzelkarten statt Schnitt.
 */
public record PublicModelRatingsResponse(
        int count,
        ModelRatingSummary.RebuyCounts rebuy,
        List<RatingAggregator.ChipCount> rebuyReasons,
        ModelRatingSummary.Categories categories,
        RatingAggregator.TotalCostCounts totalCost,
        List<RatingAggregator.ChipCount> strengths,
        List<RatingAggregator.ChipCount> weaknesses,
        List<SingleRating> singles,
        List<Quote> quotes,
        List<Report> reports,
        Aggregate aggregate) {

    /** Eine einzelne Bewertung, solange es weniger als 5 gibt. */
    public record SingleRating(
            RebuyAnswer rebuy,
            int dailyFit,
            Integer longDistance,
            int software,
            int buildQuality,
            Integer service,
            TotalCostVerdict totalCost,
            int kmRounded,
            boolean businessCar) {
    }

    /** Freigegebener Text unter 600 Zeichen. {@code month} als "2026-10". */
    public record Quote(String text, int kmRounded, String month) {
    }

    /** Freigegebener Erfahrungsbericht ab 600 Zeichen mit Messwerten als Beleg. */
    public record Report(
            String text,
            List<String> pros,
            List<String> cons,
            int kmRounded,
            String month,
            Integer logCount,
            BigDecimal consumptionKwhPer100km,
            BigDecimal ratingValue,
            LocalDate publishedOn) {
    }

    /** Für JSON-LD {@code aggregateRating}, nur ab 5 Bewertungen. */
    public record Aggregate(BigDecimal ratingValue, int ratingCount) {
    }
}
