package com.evmonitor.application.rating;

import java.math.BigDecimal;

/**
 * Bewertungen eines Modells. Zähler sind ungewichtet, Sterne-Durchschnitte nach Erfahrung gewichtet.
 * {@code categories} ist null unter {@link RatingAggregator#MIN_FOR_AVERAGE} Bewertungen.
 */
public record ModelRatingSummary(int count, RebuyCounts rebuy, Categories categories) {

    public record RebuyCounts(int yes, int brandOtherModel, int no) {
    }

    public record Categories(
            CategoryAverage dailyFit,
            CategoryAverage longDistance,
            CategoryAverage software,
            CategoryAverage buildQuality,
            CategoryAverage service) {
    }

    /** {@code average} ist null, wenn niemand die Frage beantwortet hat. */
    public record CategoryAverage(BigDecimal average, int count) {
    }
}
