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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /api/v1/sessions/{id}.
 */
class PublicApiGetSessionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("get-session-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Test Key");
        plaintextKey = created.plaintextKey();
    }

    @Test
    void getSession_returnsSessionFields() {
        EvLog log = evLogRepository.save(apiUploadLog(car.getId()));

        ResponseEntity<Map> response = get(log.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(log.getId().toString(), body.get("id").toString());
        assertNotNull(body.get("date"));
        assertNotNull(body.get("kwh"));
        assertNotNull(body.get("is_public_charging"));
    }

    @Test
    void getSession_unknownId_returns404() {
        ResponseEntity<Map> response = get(UUID.randomUUID());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getSession_foreignLog_returns403() {
        User other = createAndSaveUser("other-get-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog otherLog = evLogRepository.save(apiUploadLog(otherCar.getId()));

        ResponseEntity<Map> response = get(otherLog.getId());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void getSession_nonApiUploadLog_returns400() {
        EvLog userLog = EvLog.createNew(car.getId(), BigDecimal.valueOf(20.0), null,
                30, null, null, null, 80, LocalDateTime.now(),
                ChargingType.AC, null, null,
                false, null);
        EvLog saved = evLogRepository.save(userLog);

        ResponseEntity<Map> response = get(saved.getId());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getSession_noApiKey_returns401() {
        EvLog log = evLogRepository.save(apiUploadLog(car.getId()));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + log.getId(),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvLog apiUploadLog(UUID carId) {
        return EvLog.createFromPublicApi(
                carId,
                BigDecimal.valueOf(25.0), BigDecimal.valueOf(7.50),
                35, null, 45000,
                BigDecimal.valueOf(11.0), 80, 15,
                LocalDateTime.now().minusHours(1),
                ChargingType.AC, null, null,
                DataSource.API_UPLOAD, null,
                false, null, null);
    }

    private ResponseEntity<Map> get(UUID id) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        return restTemplate.exchange(
                "/api/v1/sessions/" + id,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
    }
}
