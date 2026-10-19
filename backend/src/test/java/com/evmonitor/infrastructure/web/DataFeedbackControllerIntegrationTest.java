package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP-Schicht von "Korrekt erkannt?": Login-Pflicht, Ownership und Eingabeprüfung vor dem Service. */
class DataFeedbackControllerIntegrationTest extends AbstractIntegrationTest {

    private User user;
    private UUID logId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("feedback-api-" + System.nanoTime() + "@example.com");
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.ID_3, new BigDecimal("58")));
        logId = evLogRepository.save(TestDataBuilder.createSmartcarStyleLog(car.getId(), LocalDateTime.now().minusDays(1),
                new BigDecimal("30"), null, ChargingType.AC, null)).getId();
    }

    @Test
    void withoutLogin_isUnauthorized() {
        assertThat(put("/api/feedback/LOG/" + logId, "{\"rating\":\"POSITIVE\"}", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/api/feedback", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void submit_thenListedAndClearable() {
        ResponseEntity<String> res = put("/api/feedback/LOG/" + logId,
                "{\"rating\":\"NEGATIVE\",\"tags\":[\"KWH_WRONG\"],\"comment\":\"waren 24\"}", user);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> mine = restTemplate.exchange("/api/feedback", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), List.class);
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody().toString()).contains(logId.toString()).contains("KWH_WRONG");

        ResponseEntity<Void> cleared = restTemplate.exchange("/api/feedback/LOG/" + logId, HttpMethod.DELETE,
                createAuthRequest(user.getId(), user.getEmail()), Void.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void foreignLog_isNotFound() {
        User stranger = createAndSaveUser("feedback-stranger-" + System.nanoTime() + "@example.com");

        assertThat(put("/api/feedback/LOG/" + logId, "{\"rating\":\"POSITIVE\"}", stranger).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidInput_isBadRequest() {
        assertThat(put("/api/feedback/LOG/" + logId, "{}", user).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put("/api/feedback/LOG/" + logId, "{\"rating\":\"MAYBE\"}", user).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put("/api/feedback/CAR/" + logId, "{\"rating\":\"POSITIVE\"}", user).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String tooLong = "{\"rating\":\"NEGATIVE\",\"comment\":\"" + "x".repeat(1001) + "\"}";
        assertThat(put("/api/feedback/LOG/" + logId, tooLong, user).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> put(String url, String body, User as) {
        HttpHeaders headers = as == null ? new HttpHeaders() : createAuthHeaders(as.getId(), as.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }
}
