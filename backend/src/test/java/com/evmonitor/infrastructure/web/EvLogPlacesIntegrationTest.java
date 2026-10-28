package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /api/logs/places: Ladeorte des eigenen Autos für die Story-Karte. Nur der Besitzer sieht sie,
 * ein fremdes Auto ist für ihn nicht vorhanden.
 */
class EvLogPlacesIntegrationTest extends AbstractIntegrationTest {

    private static final String HOME = "u1x0yz";
    private static final String SUPERCHARGER = "u1x3k7q";

    private User user;
    private UUID carId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("places-" + System.nanoTime() + "@example.com");
        carId = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3).getId();
    }

    private void saveLog(String geohash, Boolean publicCharging, String cost, String loggedAt) {
        LocalDateTime at = LocalDateTime.parse(loggedAt);
        evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("40.0"))
                .costEur(new BigDecimal(cost))
                .geohash(geohash)
                .publicCharging(publicCharging)
                .cpoName(Boolean.TRUE.equals(publicCharging) ? "Tesla Supercharger Hamburg" : null)
                .chargingType(Boolean.TRUE.equals(publicCharging) ? ChargingType.DC : ChargingType.AC)
                .loggedAt(at)
                .includeInStatistics(true)
                .createdAt(at)
                .updatedAt(at)
                .build());
    }

    private ResponseEntity<List> places(String query, User as) {
        return restTemplate.exchange("/api/logs/places?carId=" + carId + query, HttpMethod.GET,
                createAuthRequest(as.getId(), as.getEmail()), List.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerGetsPlacesOfThePeriod() {
        saveLog(HOME, false, "12.00", "2026-08-10T20:00:00");
        saveLog(HOME, false, "12.00", "2026-08-20T20:00:00");
        saveLog(SUPERCHARGER, true, "16.00", "2026-08-28T10:00:00");
        saveLog(SUPERCHARGER, true, "20.00", "2026-09-02T10:00:00");

        ResponseEntity<List> res = places("&from=2026-08-01&to=2026-08-31", user);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        List<Map<String, Object>> body = res.getBody();
        assertEquals(List.of(HOME, SUPERCHARGER), body.stream().map(p -> p.get("geohash")).toList());
        Map<String, Object> home = body.get(0);
        assertEquals("HOME", home.get("kind"));
        assertEquals(2, home.get("charges"));
        assertEquals(24.0, ((Number) home.get("costEur")).doubleValue(), 0.001);
        Map<String, Object> sc = body.get(1);
        assertEquals("PUBLIC", sc.get("kind"));
        assertEquals("Tesla Supercharger Hamburg", sc.get("cpoName"));
        assertEquals("DC", sc.get("chargingType"));
        assertEquals(0.4, ((Number) sc.get("pricePerKwh")).doubleValue(), 0.001);
    }

    @Test
    void withoutPeriodAllChargesCount() {
        saveLog(SUPERCHARGER, true, "16.00", "2026-08-28T10:00:00");
        saveLog(SUPERCHARGER, true, "20.00", "2026-09-02T10:00:00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = places("", user).getBody();

        assertEquals(2, body.get(0).get("charges"));
    }

    @Test
    void foreignCarIsNotFound() {
        saveLog(SUPERCHARGER, true, "16.00", "2026-08-28T10:00:00");
        User stranger = createAndSaveUser("places-stranger-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/logs/places?carId=" + carId, HttpMethod.GET,
                createAuthRequest(stranger.getId(), stranger.getEmail()), String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertFalse(res.getBody() != null && res.getBody().contains(SUPERCHARGER));
    }

    @Test
    void withoutLoginIsUnauthorized() {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/logs/places?carId=" + carId, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }
}
