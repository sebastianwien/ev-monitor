package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.RebuyAnswer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Einzige Stelle, an der Fahrerbewertungen zu Modellwerten verdichtet werden (persönliches Ergebnis
 * und öffentliche Modellseite). Zähler wie "17 von 23" bleiben ungewichtet, nur Sterne-Durchschnitte
 * werden nach Erfahrung gewichtet.
 */
public final class RatingAggregator {

    /** Ab hier zeigen wir Durchschnitte statt Einzelkarten. */
    public static final int MIN_FOR_AVERAGE = 5;
    /** Ab hier "kritischer als X Prozent", vorher wäre die Zahl Zufall. */
    public static final int MIN_FOR_PERCENTILE = 15;

    private static final double FULL_WEIGHT_KM = 50_000;
    private static final double MIN_WEIGHT = 0.1;
    private static final double UNIFORM_EXTREME_FACTOR = 0.5;

    private RatingAggregator() {
    }

    /** "Du bist kritischer als X Prozent" und umgekehrt, gemessen an der Gesamtnote. */
    public record Standing(int moreCriticalThanPercent, int moreSatisfiedThanPercent) {
    }

    /**
     * Gewicht einer Bewertung im Sterne-Durchschnitt: {@code clamp(km / 50.000, 0,1, 1,0)}.
     * Alle Sterne gleich und ganz oben oder ganz unten, ohne freigegebenen Text als Beleg: halbes Gewicht.
     */
    public static double weight(CarRating rating, boolean hasApprovedText) {
        double weight = Math.max(MIN_WEIGHT, Math.min(1.0, rating.getKmDriven() / FULL_WEIGHT_KM));
        return isUniformExtreme(rating) && !hasApprovedText ? weight * UNIFORM_EXTREME_FACTOR : weight;
    }

    public static ModelRatingSummary summarize(List<CarRating> ratings, Set<UUID> ratingIdsWithApprovedText) {
        ModelRatingSummary.RebuyCounts rebuy = new ModelRatingSummary.RebuyCounts(
                count(ratings, RebuyAnswer.YES), count(ratings, RebuyAnswer.BRAND_OTHER_MODEL), count(ratings, RebuyAnswer.NO));
        if (ratings.size() < MIN_FOR_AVERAGE) {
            return new ModelRatingSummary(ratings.size(), rebuy, null);
        }
        Function<CarRating, Double> w = r -> weight(r, ratingIdsWithApprovedText.contains(r.getId()));
        return new ModelRatingSummary(ratings.size(), rebuy, new ModelRatingSummary.Categories(
                average(ratings, w, CarRating::getDailyFit),
                average(ratings, w, CarRating::getLongDistance),
                average(ratings, w, CarRating::getSoftware),
                average(ratings, w, CarRating::getBuildQuality),
                average(ratings, w, CarRating::getService)));
    }

    /** @return null unter {@link #MIN_FOR_PERCENTILE} Bewertungen */
    public static Standing standing(CarRating mine, List<CarRating> ratings) {
        if (ratings.size() < MIN_FOR_PERCENTILE) return null;
        double myScore = mine.overallScore();
        List<Double> others = ratings.stream()
                .filter(r -> !r.getId().equals(mine.getId()))
                .map(CarRating::overallScore)
                .toList();
        if (others.isEmpty()) return null;
        long happier = others.stream().filter(s -> s > myScore).count();
        long sadder = others.stream().filter(s -> s < myScore).count();
        return new Standing(percent(happier, others.size()), percent(sadder, others.size()));
    }

    private static boolean isUniformExtreme(CarRating rating) {
        List<Integer> stars = rating.answeredStars();
        int first = stars.get(0);
        return (first == 1 || first == 5) && stars.stream().allMatch(s -> s == first);
    }

    private static int count(List<CarRating> ratings, RebuyAnswer answer) {
        return (int) ratings.stream().filter(r -> r.getRebuy() == answer).count();
    }

    private static ModelRatingSummary.CategoryAverage average(List<CarRating> ratings, Function<CarRating, Double> weight,
                                                              Function<CarRating, Integer> stars) {
        double weighted = 0;
        double weights = 0;
        int count = 0;
        for (CarRating r : ratings) {
            Integer value = stars.apply(r);
            if (value == null) continue;
            double w = weight.apply(r);
            weighted += w * value;
            weights += w;
            count++;
        }
        BigDecimal avg = count == 0 ? null : BigDecimal.valueOf(weighted / weights).setScale(1, RoundingMode.HALF_UP);
        return new ModelRatingSummary.CategoryAverage(avg, count);
    }

    private static int percent(long part, int total) {
        return (int) Math.round(100.0 * part / total);
    }
}
