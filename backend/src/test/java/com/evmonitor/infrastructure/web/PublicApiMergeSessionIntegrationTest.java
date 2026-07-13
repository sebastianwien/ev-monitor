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
 * Integration tests for POST /api/v1/sessions/{id}/merge.
 */
class PublicApiMergeSessionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("merge-session-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Merge Test Key");
        plaintextKey = created.plaintextKey();
    }

    @Test
    void mergeSession_ownedLogs_returns200AndDeletesSource() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));
        EvLog source = evLogRepository.save(log(car.getId(), hoursAgo(1), null, BigDecimal.valueOf(21.0)));

        ResponseEntity<Map> response = merge(target.getId(), source.getId(), false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(target.getId().toString(), response.getBody().get("id"));
        assertEquals(22.5, (Double) response.getBody().get("kwh"), 0.001);
        assertEquals(21.0, (Double) response.getBody().get("kwh_at_vehicle"), 0.001);
        assertFalse(evLogRepository.findById(source.getId()).isPresent());
    }

    @Test
    void mergeSession_preferSource_sourceValuesWin() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));
        EvLog source = evLogRepository.save(log(car.getId(), hoursAgo(1), BigDecimal.valueOf(19.0), null));

        ResponseEntity<Map> response = merge(target.getId(), source.getId(), true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(19.0, (Double) response.getBody().get("kwh"), 0.001);
    }

    @Test
    void mergeSession_unknownSource_returns404() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));

        ResponseEntity<Map> response = merge(target.getId(), UUID.randomUUID(), false);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void mergeSession_foreignLog_returns403() {
        User other = createAndSaveUser("other-merge-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));
        EvLog foreign = evLogRepository.save(log(otherCar.getId(), hoursAgo(1), null, BigDecimal.valueOf(21.0)));

        ResponseEntity<Map> response = merge(target.getId(), foreign.getId(), false);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(evLogRepository.findById(foreign.getId()).isPresent());
    }

    @Test
    void mergeSession_differentCars_returns409() {
        Car secondCar = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));
        EvLog source = evLogRepository.save(log(secondCar.getId(), hoursAgo(1), null, BigDecimal.valueOf(21.0)));

        ResponseEntity<Map> response = merge(target.getId(), source.getId(), false);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void mergeSession_outsideMergeWindow_returns409() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(30), BigDecimal.valueOf(22.5), null));
        EvLog source = evLogRepository.save(log(car.getId(), hoursAgo(1), null, BigDecimal.valueOf(21.0)));

        ResponseEntity<Map> response = merge(target.getId(), source.getId(), false);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("MERGE_WINDOW_EXCEEDED", response.getBody().get("code"));
        assertTrue(evLogRepository.findById(source.getId()).isPresent());
    }

    @Test
    void mergeSession_sameLog_returns409() {
        EvLog log = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));

        ResponseEntity<Map> response = merge(log.getId(), log.getId(), false);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void mergeSession_missingSourceId_returns400() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));

        HttpHeaders headers = apiHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + target.getId() + "/merge",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("prefer_source", false), headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void mergeSession_noApiKey_returns401() {
        EvLog target = evLogRepository.save(log(car.getId(), hoursAgo(2), BigDecimal.valueOf(22.5), null));
        EvLog source = evLogRepository.save(log(car.getId(), hoursAgo(1), null, BigDecimal.valueOf(21.0)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + target.getId() + "/merge",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("source_session_id", source.getId().toString()), headers),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    private EvLog log(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged, BigDecimal kwhAtVehicle) {
        return EvLog.createFromPublicApi(
                carId,
                kwhCharged, kwhAtVehicle, BigDecimal.valueOf(7.50),
                35, null, 45000,
                BigDecimal.valueOf(11.0), new BigDecimal("80"), new BigDecimal("15"),
                loggedAt,
                ChargingType.AC, null, null,
                DataSource.API_UPLOAD, null,
                false, null, null, null);
    }

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        return headers;
    }

    private ResponseEntity<Map> merge(UUID targetId, UUID sourceId, boolean preferSource) {
        HttpHeaders headers = apiHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "source_session_id", sourceId.toString(),
                "prefer_source", preferSource);
        return restTemplate.exchange(
                "/api/v1/sessions/" + targetId + "/merge",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }
}
