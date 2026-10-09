package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP-Schicht der Fahrerbewertung: Login-Pflicht, Ownership und Eingabeprüfung vor dem Service. */
class CarRatingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID = """
            {"rebuy":"YES","rebuyReasons":[],"dailyFit":4,"longDistance":null,"software":4,"buildQuality":5,
             "service":3,"totalCost":"EXPECTED","strengths":["EFFICIENCY"],"weaknesses":[]}
            """;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("rating-api-" + System.nanoTime() + "@example.com");
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.ID_3, new BigDecimal("58")));
        for (int i = 0; i < 5; i++) {
            evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestampAndOdometer(car.getId(), new BigDecimal("30"),
                    new BigDecimal("10"), LocalDateTime.now().minusDays(i + 1), 40_000 + i * 100));
        }
    }

    @Test
    void withoutLogin_isUnauthorized() {
        assertThat(restTemplate.getForEntity("/api/ratings/eligibility", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(put("/api/cars/" + car.getId() + "/rating", VALID, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void submit_returnsResultWithCoins_thenRatingIsReadable() {
        ResponseEntity<Map> res = put("/api/cars/" + car.getId() + "/rating", VALID, user);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("coinsAwarded", 50);

        ResponseEntity<Map> mine = restTemplate.exchange("/api/cars/" + car.getId() + "/rating", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody().toString()).doesNotContain("llmCheck");
    }

    @Test
    void myRating_withoutRating_isNoContent() {
        ResponseEntity<String> res = restTemplate.exchange("/api/cars/" + car.getId() + "/rating", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void eligibility_listsOwnCars() {
        ResponseEntity<Map> res = restTemplate.exchange("/api/ratings/eligibility", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("cars").toString()).contains(car.getId().toString()).contains("eligible=true")
                .contains("modelName=ID.3");
    }

    @Test
    void foreignCar_isForbidden() {
        User stranger = createAndSaveUser("rating-stranger-" + System.nanoTime() + "@example.com");

        assertThat(put("/api/cars/" + car.getId() + "/rating", VALID, stranger).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void starOutOfRange_isBadRequest() {
        String body = VALID.replace("\"dailyFit\":4", "\"dailyFit\":6");

        assertThat(put("/api/cars/" + car.getId() + "/rating", body, user).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRebuy_isBadRequest() {
        String body = VALID.replace("\"rebuy\":\"YES\",", "");

        assertThat(put("/api/cars/" + car.getId() + "/rating", body, user).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownChip_isBadRequest() {
        String body = VALID.replace("\"EFFICIENCY\"", "\"<script>\"");

        assertThat(put("/api/cars/" + car.getId() + "/rating", body, user).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reviewTooLong_isBadRequest() {
        put("/api/cars/" + car.getId() + "/rating", VALID, user);
        String body = "{\"body\":\"" + "x".repeat(5_001) + "\",\"pros\":[],\"cons\":[]}";

        assertThat(put("/api/cars/" + car.getId() + "/rating/review", body, user).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reviewSettings_withoutFlag_isBadRequest() {
        put("/api/cars/" + car.getId() + "/rating", VALID, user);

        ResponseEntity<String> res = restTemplate.exchange("/api/cars/" + car.getId() + "/rating/review/settings",
                HttpMethod.PATCH, json("{}", user), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> put(String url, String body, User as) {
        return restTemplate.exchange(url, HttpMethod.PUT, json(body, as), Map.class);
    }

    private HttpEntity<String> json(String body, User as) {
        HttpHeaders headers = as == null ? new HttpHeaders() : createAuthHeaders(as.getId(), as.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
