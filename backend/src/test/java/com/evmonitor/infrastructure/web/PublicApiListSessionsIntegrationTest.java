package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicApiListSessionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("list-sessions-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Test Key");
        plaintextKey = created.plaintextKey();
    }

    @Test
    void listSessions_withCarId_returnsSessionsForCar() {
        evLogRepository.save(session(car.getId(), LocalDateTime.now().minusDays(1)));
        evLogRepository.save(session(car.getId(), LocalDateTime.now().minusDays(2)));

        ResponseEntity<Map> response = list("car_id=" + car.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, ((List<?>) body.get("sessions")).size());
        assertEquals(2, ((Number) body.get("total")).intValue());
        assertEquals(0, ((Number) body.get("page")).intValue());
        assertEquals(20, ((Number) body.get("size")).intValue());
        assertFalse((Boolean) body.get("has_more"));
    }

    @Test
    void listSessions_withoutCarId_returnsAllUserSessions() {
        Car car2 = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_Y);
        evLogRepository.save(session(car.getId(), LocalDateTime.now().minusDays(1)));
        evLogRepository.save(session(car2.getId(), LocalDateTime.now().minusDays(2)));

        ResponseEntity<Map> response = list("");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, ((List<?>) response.getBody().get("sessions")).size());
    }

    @Test
    void listSessions_pagination_works() {
        for (int i = 0; i < 5; i++) {
            evLogRepository.save(session(car.getId(), LocalDateTime.now().minusDays(i + 1)));
        }

        ResponseEntity<Map> first = list("car_id=" + car.getId() + "&size=3&page=0");
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(3, ((List<?>) first.getBody().get("sessions")).size());
        assertTrue((Boolean) first.getBody().get("has_more"));
        assertEquals(5, ((Number) first.getBody().get("total")).intValue());

        ResponseEntity<Map> second = list("car_id=" + car.getId() + "&size=3&page=1");
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(2, ((List<?>) second.getBody().get("sessions")).size());
        assertFalse((Boolean) second.getBody().get("has_more"));
    }

    @Test
    void listSessions_dateFilter_filtersCorrectly() {
        evLogRepository.save(session(car.getId(), LocalDateTime.of(2024, 6, 1, 12, 0)));
        evLogRepository.save(session(car.getId(), LocalDateTime.of(2024, 3, 15, 8, 0)));
        evLogRepository.save(session(car.getId(), LocalDateTime.of(2024, 1, 5, 10, 0)));

        ResponseEntity<Map> response = list("car_id=" + car.getId() + "&from=2024-02-01&to=2024-12-31");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, ((List<?>) response.getBody().get("sessions")).size());
    }

    @Test
    void listSessions_noApiKey_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void listSessions_foreignCar_returns403() {
        User other = createAndSaveUser("other-list-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<Map> response = list("car_id=" + otherCar.getId());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listSessions_unknownCarId_returns403() {
        ResponseEntity<Map> response = list("car_id=" + UUID.randomUUID());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listSessions_emptyResult_returnsEmptyPage() {
        ResponseEntity<Map> response = list("car_id=" + car.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertEquals(0, ((List<?>) body.get("sessions")).size());
        assertEquals(0, ((Number) body.get("total")).intValue());
        assertFalse((Boolean) body.get("has_more"));
    }

    @Test
    void listSessions_responseContainsCarIdAndDataSource() {
        evLogRepository.save(session(car.getId(), LocalDateTime.now().minusHours(1)));

        ResponseEntity<Map> response = list("car_id=" + car.getId());

        Map<?, ?> session = (Map<?, ?>) ((List<?>) response.getBody().get("sessions")).get(0);
        assertEquals(car.getId().toString(), session.get("car_id").toString());
        assertEquals("API_UPLOAD", session.get("data_source"));
    }

    @Test
    void listSessions_sortedNewestFirst() {
        LocalDateTime older = LocalDateTime.now().minusDays(5);
        LocalDateTime newer = LocalDateTime.now().minusDays(1);
        evLogRepository.save(session(car.getId(), older));
        evLogRepository.save(session(car.getId(), newer));

        ResponseEntity<Map> response = list("car_id=" + car.getId());

        List<?> sessions = (List<?>) response.getBody().get("sessions");
        String firstDate = (String) ((Map<?, ?>) sessions.get(0)).get("date");
        String secondDate = (String) ((Map<?, ?>) sessions.get(1)).get("date");
        assertTrue(firstDate.compareTo(secondDate) > 0, "Sessions should be newest first");
    }

    @Test
    void listSessions_sizeExceeds100_returns400() {
        ResponseEntity<Map> response = list("size=101");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EvLog session(UUID carId, LocalDateTime at) {
        return EvLog.createFromPublicApi(
                carId,
                BigDecimal.valueOf(25.0), null, BigDecimal.valueOf(7.50),
                35, null, 45000,
                BigDecimal.valueOf(11.0), new BigDecimal("80"), new BigDecimal("15"),
                at,
                ChargingType.AC, null, null,
                DataSource.API_UPLOAD, null,
                false, null, null);
    }

    private ResponseEntity<Map> list(String queryParams) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        String url = "/api/v1/sessions" + (queryParams.isBlank() ? "" : "?" + queryParams);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
