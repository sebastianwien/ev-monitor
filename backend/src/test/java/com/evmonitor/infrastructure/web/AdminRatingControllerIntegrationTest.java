package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.CarReviewRepository;
import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingSnapshot;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.ReviewDraft;
import com.evmonitor.domain.rating.ReviewProof;
import com.evmonitor.domain.rating.ReviewStatus;
import com.evmonitor.domain.rating.TotalCostVerdict;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.email.ReviewRejectedMail;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** Moderation nur für Admins, Schwärzung geprüft, Statuswechsel, Coins und Ablehnungs-Mail. */
class AdminRatingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired CarRatingRepository ratingRepository;
    @Autowired CarReviewRepository reviewRepository;
    @MockitoBean EmailService emailService;

    private User admin;
    private User driver;
    private CarRating rating;

    @BeforeEach
    void setUp() {
        admin = createAndSaveAdminUser("rating-admin-" + System.nanoTime() + "@example.com");
        driver = createAndSaveUser("rating-driver-" + System.nanoTime() + "@example.com");
        rating = ratingRepository.save(CarRating.create(null, driver.getId(),
                new RatingSnapshot(CarBrand.CarModel.ID_3, null, 10_000, 10, false, false),
                new RatingAnswers(RebuyAnswer.YES, List.of(), 4, null, 4, 4, null, TotalCostVerdict.EXPECTED, List.of(), List.of())));
    }

    private CarReview submit(String body) {
        return reviewRepository.save(CarReview.submit(rating.getId(), ReviewDraft.of(body, List.of(), List.of()),
                new ReviewProof(10_000, 10, null), false));
    }

    private ResponseEntity<String> post(String url, String json, User as) {
        HttpHeaders headers = createAuthHeaders(as.getId(), as.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    @Test
    void nonAdmin_isForbidden() {
        CarReview review = submit("Text.");
        assertThat(restTemplate.exchange("/api/admin/ratings", HttpMethod.GET,
                createAuthRequest(driver.getId(), driver.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/admin/ratings/reviews/" + review.getId() + "/approve", "{}", driver).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void list_showsOpenTextsFirst_withoutEmail() {
        submit("Offener Text vom Fahrer.");
        ResponseEntity<String> res = restTemplate.exchange("/api/admin/ratings?status=PENDING", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains(rating.getId().toString()).doesNotContain(driver.getEmail());
    }

    @Test
    void approve_withRedaction_publishes_supersedesOlder_andPaysCoinsOnce() {
        CarReview older = submit("Alte Fassung.");
        post("/api/admin/ratings/reviews/" + older.getId() + "/approve", "{}", admin);
        CarReview review = submit("Händler Meier war super.");

        ResponseEntity<String> res = post("/api/admin/ratings/reviews/" + review.getId() + "/approve",
                "{\"publishedBody\":\"Händler [entfernt] war super.\"}", admin);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().getPublishedBody()).isEqualTo("Händler [entfernt] war super.");
        assertThat(reviewRepository.findById(older.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.SUPERSEDED);
        assertThat(coinLogRepository.findAllByUserId(driver.getId()).stream()
                .filter(c -> "Erfahrung veröffentlicht".equals(c.getActionDescription()))
                .count()).isEqualTo(2);
    }

    @Test
    void approve_withEditedText_isBadRequest() {
        CarReview review = submit("Alles gut.");
        assertThat(post("/api/admin/ratings/reviews/" + review.getId() + "/approve",
                "{\"publishedBody\":\"Alles sehr gut.\"}", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reject_otherWithoutNote_isBadRequest_withReason_mailsDriver() {
        CarReview review = submit("Werbung für Händler X.");
        assertThat(post("/api/admin/ratings/reviews/" + review.getId() + "/reject", "{\"reason\":\"OTHER\"}", admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> res = post("/api/admin/ratings/reviews/" + review.getId() + "/reject",
                "{\"reason\":\"ADVERTISING\"}", admin);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.REJECTED);
        verify(emailService).sendReviewRejectedEmail(any(ReviewRejectedMail.class));
    }
}
