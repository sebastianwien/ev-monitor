package com.evmonitor.application.rating;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingChip;
import com.evmonitor.domain.rating.RatingSnapshot;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.ReviewDraft;
import com.evmonitor.domain.rating.ReviewProof;
import com.evmonitor.domain.rating.TotalCostVerdict;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Öffentliche Bewertungen einer Modellseite gegen das echte Schema. Jeder Test nutzt ein eigenes
 * Modell, weil sich alle Postgres-ITs eine Datenbank und den Cache teilen.
 */
class PublicModelRatingsServicePostgresIT extends AbstractPostgresIT {

    @Autowired PublicModelRatingsService service;
    @Autowired CarRatingRepository ratingRepository;
    @Autowired CarReviewRepository reviewRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void belowFive_showsSingleCards_withoutAveragesOrAggregate() {
        rate(CarBrand.CarModel.R1T, 23_700, RebuyAnswer.YES, false);
        rate(CarBrand.CarModel.R1T, 2_000, RebuyAnswer.NO, true);

        PublicModelRatingsResponse response = service.forSlug("Rivian", "R1T").orElseThrow();

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.categories()).isNull();
        assertThat(response.aggregate()).isNull();
        assertThat(response.totalCost()).isNull();
        assertThat(response.singles()).hasSize(2);
        assertThat(response.singles()).extracting(PublicModelRatingsResponse.SingleRating::kmRounded)
                .containsExactlyInAnyOrder(25_000, 0);
        assertThat(response.singles()).anyMatch(PublicModelRatingsResponse.SingleRating::businessCar);
    }

    @Test
    void fromFive_showsAveragesCountsTopChipsAndAggregate() {
        for (int i = 0; i < 5; i++) rate(CarBrand.CarModel.R1S, 50_000, i < 3 ? RebuyAnswer.YES : RebuyAnswer.NO, false);

        PublicModelRatingsResponse response = service.forSlug("Rivian", "R1S").orElseThrow();

        assertThat(response.count()).isEqualTo(5);
        assertThat(response.rebuy().yes()).isEqualTo(3);
        assertThat(response.categories().dailyFit().average()).isEqualByComparingTo(new BigDecimal("4.0"));
        assertThat(response.totalCost().cheaper()).isEqualTo(5);
        assertThat(response.strengths()).first().isEqualTo(new RatingAggregator.ChipCount("SPACE", 5));
        assertThat(response.aggregate().ratingCount()).isEqualTo(5);
        assertThat(response.aggregate().ratingValue()).isNotNull();
        assertThat(response.singles()).isEmpty();
    }

    @Test
    void seedIsExcluded_anonymizedRatingsCount() {
        rate(CarBrand.CarModel.R2, 10_000, RebuyAnswer.YES, false);
        CarRating anonymized = rate(CarBrand.CarModel.R2, 10_000, RebuyAnswer.YES, false);
        anonymized.anonymize();
        ratingRepository.save(anonymized);
        ratingRepository.save(CarRating.create(null, null,
                new RatingSnapshot(CarBrand.CarModel.R2, null, 10_000, 10, false, true), answers(RebuyAnswer.NO)));

        PublicModelRatingsResponse response = service.forSlug("Rivian", "R2").orElseThrow();

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.rebuy().no()).isZero();
    }

    @Test
    void onlyApprovedTextsAppear_asPublishedBody_splitIntoQuotesAndReports() {
        CarRating rating = rate(CarBrand.CarModel.AIR, 31_000, RebuyAnswer.YES, false);
        review(rating, "Offen, noch nicht geprüft.", "PENDING", null);
        review(rating, "Abgelehnt wegen Kennzeichen.", "REJECTED", null);
        review(rating, "Kurz und gut, Händler Meier war super.", "APPROVED", "Kurz und gut, Händler [entfernt] war super.");
        CarRating other = rate(CarBrand.CarModel.AIR, 8_000, RebuyAnswer.YES, false);
        String longText = "Langstrecke klappt. ".repeat(40);
        review(other, longText, "APPROVED", longText);

        PublicModelRatingsResponse response = service.forSlug("Lucid", "Air").orElseThrow();

        assertThat(response.quotes()).singleElement().satisfies(q -> {
            assertThat(q.text()).isEqualTo("Kurz und gut, Händler [entfernt] war super.");
            assertThat(q.kmRounded()).isEqualTo(30_000);
            assertThat(q.month()).matches("\\d{4}-\\d{2}");
        });
        assertThat(response.reports()).singleElement().satisfies(r -> {
            assertThat(r.text()).isEqualTo(longText);
            assertThat(r.pros()).containsExactly("Platz");
            assertThat(r.logCount()).isEqualTo(12);
            assertThat(r.ratingValue()).isNotNull();
        });
    }

    @Test
    void quotes_areCappedAtThree_newestFirst() {
        for (int i = 1; i <= 4; i++) {
            CarRating rating = rate(CarBrand.CarModel.GRAVITY, 10_000, RebuyAnswer.YES, false);
            CarReview review = review(rating, "Zitat " + i, "APPROVED", "Zitat " + i);
            jdbc.update("UPDATE car_review SET reviewed_at = now() - (? * interval '1 day') WHERE id = ?", 10 - i, review.getId());
        }

        PublicModelRatingsResponse response = service.forSlug("Lucid", "Gravity").orElseThrow();

        assertThat(response.quotes()).extracting(PublicModelRatingsResponse.Quote::text)
                .containsExactly("Zitat 4", "Zitat 3", "Zitat 2");
    }

    @Test
    void json_containsNoIdsAndNoPersonalReference() throws Exception {
        CarRating rating = rate(CarBrand.CarModel.OCEAN, 10_000, RebuyAnswer.YES, false);
        CarReview review = review(rating, "Ohne Bezug.", "APPROVED", "Ohne Bezug.");

        String json = objectMapper.writeValueAsString(service.forSlug("Fisker", "Ocean").orElseThrow());

        assertThat(json).doesNotContain(rating.getId().toString(), review.getId().toString())
                .doesNotContain("userId", "carId", "ratingId", "\"id\"");
    }

    @Test
    void unknownModel_isEmpty() {
        assertThat(service.forSlug("Rivian", "R9")).isEmpty();
    }

    // ── Helfer ─────────────────────────────────────────────────────────────

    private static RatingAnswers answers(RebuyAnswer rebuy) {
        return new RatingAnswers(rebuy, List.of(), 4, null, 4, 4, null, TotalCostVerdict.CHEAPER,
                List.of(RatingChip.SPACE), List.of(RatingChip.SOFTWARE));
    }

    private CarRating rate(CarBrand.CarModel model, int km, RebuyAnswer rebuy, boolean businessCar) {
        RatingSnapshot snapshot = new RatingSnapshot(model, null, km, 12, businessCar, false);
        return ratingRepository.save(CarRating.create(null, null, snapshot, answers(rebuy)));
    }

    private CarReview review(CarRating rating, String body, String status, String publishedBody) {
        CarReview review = reviewRepository.save(CarReview.submit(rating.getId(),
                ReviewDraft.of(body, List.of("Platz"), new ArrayList<>()), new ReviewProof(rating.getKmDriven(), 12, null), false));
        jdbc.update("UPDATE car_review SET status = ?, published_body = ?, reviewed_at = now() WHERE id = ?",
                status, publishedBody, review.getId());
        return review;
    }
}
