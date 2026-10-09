package com.evmonitor.application.rating;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingSnapshot;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.TotalCostVerdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Aggregation der Fahrerbewertungen eines Modells: Schwellen, Gewichtung und persönliche Einordnung.
 * Zähler bleiben ungewichtet, nur Sterne-Durchschnitte werden nach Erfahrung (km) gewichtet.
 */
class RatingAggregatorTest {

    private static RatingAnswers stars(int dailyFit, Integer longDistance, int software, int buildQuality,
                                       Integer service) {
        return new RatingAnswers(RebuyAnswer.YES, List.of(), dailyFit, longDistance, software, buildQuality,
                service, TotalCostVerdict.EXPECTED, List.of(), List.of());
    }

    private static RatingAnswers rebuy(RebuyAnswer answer) {
        return new RatingAnswers(answer, List.of(), 3, 3, 3, 3, 3, TotalCostVerdict.EXPECTED, List.of(), List.of());
    }

    private static CarRating rating(int km, RatingAnswers answers) {
        RatingSnapshot snapshot = new RatingSnapshot(CarBrand.CarModel.ID_3, null, km, 10, false, false);
        return CarRating.create(UUID.randomUUID(), UUID.randomUUID(), snapshot, answers);
    }

    private static List<CarRating> uniform(int n, int km, int star) {
        List<CarRating> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(rating(km, stars(star, star, star, star, star)));
        return list;
    }

    @Test
    void weight_followsKmDriven_clampedBetweenTenthAndOne() {
        assertThat(RatingAggregator.weight(rating(0, stars(3, 4, 3, 4, 3)), false)).isEqualTo(0.1);
        assertThat(RatingAggregator.weight(rating(25_000, stars(3, 4, 3, 4, 3)), false)).isEqualTo(0.5);
        assertThat(RatingAggregator.weight(rating(120_000, stars(3, 4, 3, 4, 3)), false)).isEqualTo(1.0);
    }

    @Test
    void weight_halvesUniformExtremes_unlessAnApprovedTextBacksThem() {
        CarRating allFive = rating(50_000, stars(5, 5, 5, 5, 5));
        CarRating allOne = rating(50_000, stars(1, null, 1, 1, null));
        CarRating allThree = rating(50_000, stars(3, 3, 3, 3, 3));

        assertThat(RatingAggregator.weight(allFive, false)).isEqualTo(0.5);
        assertThat(RatingAggregator.weight(allOne, false)).isEqualTo(0.5);
        assertThat(RatingAggregator.weight(allFive, true)).isEqualTo(1.0);
        assertThat(RatingAggregator.weight(allThree, false)).isEqualTo(1.0);
    }

    @Test
    void belowFiveRatings_countsRebuy_butShowsNoAverages() {
        List<CarRating> ratings = List.of(
                rating(10_000, rebuy(RebuyAnswer.YES)),
                rating(10_000, rebuy(RebuyAnswer.YES)),
                rating(10_000, rebuy(RebuyAnswer.BRAND_OTHER_MODEL)),
                rating(10_000, rebuy(RebuyAnswer.NO)));

        ModelRatingSummary summary = RatingAggregator.summarize(ratings, Set.of());

        assertThat(summary.count()).isEqualTo(4);
        assertThat(summary.rebuy()).isEqualTo(new ModelRatingSummary.RebuyCounts(2, 1, 1));
        assertThat(summary.categories()).isNull();
    }

    @Test
    void fromFiveRatings_averagesAreWeightedByExperience() {
        List<CarRating> ratings = new ArrayList<>(uniform(4, 50_000, 4));
        // Kaum Erfahrung (w = 0,1) und zugleich gleichförmiges Extrem (w x 0,5): zieht den Schnitt kaum.
        ratings.add(rating(0, stars(1, 1, 1, 1, 1)));

        ModelRatingSummary summary = RatingAggregator.summarize(ratings, Set.of());

        assertThat(summary.count()).isEqualTo(5);
        // (4 * 1,0 * 4 + 0,05 * 1) / 4,05 = 3,96 -> 4,0
        assertThat(summary.categories().dailyFit().average()).isEqualByComparingTo(new BigDecimal("4.0"));
        assertThat(summary.categories().dailyFit().count()).isEqualTo(5);
    }

    @Test
    void optionalCategories_countOnlyDriversWhoAnswered() {
        List<CarRating> ratings = new ArrayList<>(uniform(3, 50_000, 4));
        ratings.add(rating(50_000, stars(4, null, 4, 4, null)));
        ratings.add(rating(50_000, stars(2, null, 4, 4, null)));

        ModelRatingSummary summary = RatingAggregator.summarize(ratings, Set.of());

        assertThat(summary.categories().longDistance().count()).isEqualTo(3);
        assertThat(summary.categories().service().count()).isEqualTo(3);
        assertThat(summary.categories().dailyFit().count()).isEqualTo(5);
        assertThat(summary.categories().dailyFit().average()).isEqualByComparingTo(new BigDecimal("3.6"));
    }

    @Test
    void overallScore_isMeanOfAnsweredStars() {
        assertThat(rating(1, stars(5, null, 3, 4, null)).overallScore()).isCloseTo(4.0, within(0.001));
        assertThat(rating(1, stars(5, 1, 3, 4, 2)).overallScore()).isCloseTo(3.0, within(0.001));
    }

    @Test
    void standing_onlyFromFifteenRatings() {
        List<CarRating> fourteen = uniform(14, 10_000, 3);
        assertThat(RatingAggregator.standing(fourteen.get(0), fourteen)).isNull();

        List<CarRating> fifteen = uniform(15, 10_000, 3);
        assertThat(RatingAggregator.standing(fifteen.get(0), fifteen)).isNotNull();
    }

    @Test
    void standing_comparesOverallScoreAgainstAllOtherDrivers() {
        CarRating mine = rating(10_000, stars(2, 2, 2, 2, 2));
        List<CarRating> ratings = new ArrayList<>();
        ratings.add(mine);
        ratings.addAll(uniform(7, 10_000, 4));   // zufriedener als ich
        ratings.addAll(uniform(4, 10_000, 1));   // kritischer als ich
        ratings.addAll(uniform(3, 10_000, 2));   // gleich

        RatingAggregator.Standing standing = RatingAggregator.standing(mine, ratings);

        // 14 andere: 7 bewerten besser (50 %), 4 schlechter (29 %)
        assertThat(standing.moreCriticalThanPercent()).isEqualTo(50);
        assertThat(standing.moreSatisfiedThanPercent()).isEqualTo(29);
    }
}
