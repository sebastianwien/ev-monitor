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
 * Integration tests for DELETE /api/v1/sessions/{id}.
 */
class PublicApiDeleteSessionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("delete-session-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Delete Test Key");
        plaintextKey = created.plaintextKey();
    }

    @Test
    void deleteSession_ownedLog_returns204() {
        EvLog log = evLogRepository.save(apiUploadLog(car.getId()));

        ResponseEntity<Void> response = delete(log.getId());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(evLogRepository.findById(log.getId()).isPresent());
    }

    @Test
    void deleteSession_unknownId_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                new HttpEntity<>(apiHeaders()),
                Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteSession_foreignLog_returns403() {
        User other = createAndSaveUser("other-del-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog otherLog = evLogRepository.save(apiUploadLog(otherCar.getId()));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + otherLog.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(apiHeaders()),
                Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteSession_noApiKey_returns401() {
        EvLog log = evLogRepository.save(apiUploadLog(car.getId()));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + log.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvLog apiUploadLog(UUID carId) {
        return EvLog.createFromPublicApi(
                carId,
                BigDecimal.valueOf(25.0), null, BigDecimal.valueOf(7.50),
                35, null, 45000,
                BigDecimal.valueOf(11.0), new BigDecimal("80"), new BigDecimal("15"),
                LocalDateTime.now().minusHours(1),
                ChargingType.AC, null, null,
                DataSource.API_UPLOAD, null,
                false, null, null, null);
    }

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        return headers;
    }

    private ResponseEntity<Void> delete(UUID id) {
        return restTemplate.exchange(
                "/api/v1/sessions/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(apiHeaders()),
                Void.class);
    }
}
