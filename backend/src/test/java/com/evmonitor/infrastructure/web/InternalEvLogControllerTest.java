package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for InternalEvLogController.
 *
 * Verifies:
 * - POST /api/internal/logs: create log from internal service (TESLA_FLEET, WALLBOX_GOE etc.)
 * - PATCH /api/internal/logs/geohash: update geohash of an existing log
 * - Auth: missing/wrong X-Internal-Token → 403
 */
class InternalEvLogControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    private User testUser;
    private Car testCar;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("internal-evlog-" + System.nanoTime() + "@example.com");
        testCar = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3);
    }

    // --- POST /api/internal/logs ---

    @Test
    void createLog_withValidInternalToken_returnsCreatedLog() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.5", 60, LocalDateTime.now().minusHours(1), null, "TESLA_FLEET_IMPORT", "14.50");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testCar.getId().toString(), response.getBody().get("carId").toString());
    }

    @Test
    void createLog_withCostAndGeohash_persistsBothFields() {
        LocalDateTime loggedAt = LocalDateTime.now().minusHours(2);
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "50.0", 90, loggedAt, "u2edq", "TESLA_FLEET_IMPORT", "18.00");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals("u2edq", body.get("geohash"));
        assertEquals("18.0", body.get("costEur").toString());
    }

    @Test
    void createLog_withoutCostOrGeohash_createsLogWithNulls() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "30.0", 45, LocalDateTime.now().minusHours(3), null, "WALLBOX_GOE", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().get("geohash"));
        assertNull(response.getBody().get("costEur"));
    }

    @Test
    void createLog_withoutInternalToken_returns403() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.0", 60, LocalDateTime.now(), null, "TESLA_FLEET_IMPORT", null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createLog_withWrongInternalToken_returns403() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.0", 60, LocalDateTime.now(), null, "TESLA_FLEET_IMPORT", null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders("wrong-token")), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // --- PATCH /api/internal/logs/geohash ---

    @Test
    void updateGeohash_updatesGeohashOnExistingLog() {
        // Create a log first (without geohash)
        LocalDateTime loggedAt = LocalDateTime.now().minusHours(5).withNano(0).withSecond(0);
        Map<String, Object> createRequest = logRequest(testCar.getId(), testUser.getId(),
                "60.0", 75, loggedAt, null, "TESLA_FLEET_IMPORT", "22.50");
        restTemplate.exchange("/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(createRequest, internalHeaders(VALID_TOKEN)), Map.class);

        // Patch the geohash
        Map<String, Object> patchRequest = Map.of(
                "carId", testCar.getId().toString(),
                "userId", testUser.getId().toString(),
                "loggedAt", loggedAt.toString(),
                "geohash", "u2edq"
        );

        ResponseEntity<Void> patchResponse = restTemplate.exchange(
                "/api/internal/logs/geohash", HttpMethod.PATCH,
                new HttpEntity<>(patchRequest, internalHeaders(VALID_TOKEN)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, patchResponse.getStatusCode());

        // Verify via user-authenticated GET
        String userToken = createAuthHeaders(testUser.getId(), testUser.getEmail())
                .getFirst("Authorization").substring(7);
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("Authorization", "Bearer " + userToken);
        ResponseEntity<Map[]> logsResponse = restTemplate.exchange(
                "/api/logs", HttpMethod.GET,
                new HttpEntity<>(userHeaders), Map[].class);

        assertEquals(HttpStatus.OK, logsResponse.getStatusCode());
        Map[] logs = logsResponse.getBody();
        assertNotNull(logs);
        boolean geohashFound = java.util.Arrays.stream(logs)
                .anyMatch(log -> "u2edq".equals(log.get("geohash")));
        assertTrue(geohashFound, "Geohash should be updated to u2edq");
    }

    @Test
    void updateGeohash_withoutInternalToken_returns403() {
        Map<String, Object> request = Map.of(
                "carId", testCar.getId().toString(),
                "userId", testUser.getId().toString(),
                "loggedAt", LocalDateTime.now().toString(),
                "geohash", "u2edq"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs/geohash", HttpMethod.PATCH,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // --- Provider cost auto-calculation ---

    @Test
    void createSmartcarLog_withPreviousProviderAtGeohash_calculatesCostFromTariff() {
        // Arrange: AC tariff (0.39 €/kWh, no session fee)
        UserChargingProviderEntity provider = new UserChargingProviderEntity();
        provider.setUserId(testUser.getId());
        provider.setProviderName("Heimladung");
        provider.setAcPricePerKwh(new BigDecimal("0.3900"));
        provider.setDcPricePerKwh(null);
        provider.setMonthlyFeeEur(BigDecimal.ZERO);
        provider.setSessionFeeEur(BigDecimal.ZERO);
        provider.setActiveFrom(LocalDate.now().minusDays(30));
        UserChargingProviderEntity savedProvider = chargingProviderRepository.save(provider);

        // Previous log at geohash with provider assigned (simulates a prior manual edit)
        String geohash = "u2ey3d7";
        EvLog previousLog = EvLog.createFromInternal(
                testCar.getId(), new BigDecimal("50.0"), 120, geohash,
                LocalDateTime.now().minusDays(1), null, null,
                DataSource.SMARTCAR_LIVE, null, ChargingType.AC,
                60000, 20, 90, null)
                .toBuilder().chargingProviderId(savedProvider.getId()).build();
        evLogRepository.save(previousLog);

        // Act: new Smartcar log at same geohash, no costEur
        // 40 kWh / 2h = 20 kW avg → AC inferred → 40 × 0.39 = 15.60 EUR expected
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "40.0", 120, LocalDateTime.now().minusHours(1), geohash, "SMARTCAR_LIVE", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("costEur"), "costEur should be auto-calculated from tariff");
        assertEquals(0, new BigDecimal(body.get("costEur").toString()).compareTo(new BigDecimal("15.60")));
        assertEquals(savedProvider.getId().toString(), body.get("chargingProviderId").toString());
    }

    @Test
    void createSmartcarLog_withSessionFeeProvider_includesSessionFeeInCost() {
        UserChargingProviderEntity provider = new UserChargingProviderEntity();
        provider.setUserId(testUser.getId());
        provider.setProviderName("IONITY");
        provider.setAcPricePerKwh(null);
        provider.setDcPricePerKwh(new BigDecimal("0.7900"));
        provider.setMonthlyFeeEur(BigDecimal.ZERO);
        provider.setSessionFeeEur(new BigDecimal("1.00"));
        provider.setActiveFrom(LocalDate.now().minusDays(30));
        UserChargingProviderEntity savedProvider = chargingProviderRepository.save(provider);

        String geohash = "u2ey3d8";
        // Previous log with DC + provider (100 kWh / 1h = 100 kW → DC)
        EvLog previousLog = EvLog.createFromInternal(
                testCar.getId(), new BigDecimal("100.0"), 60, geohash,
                LocalDateTime.now().minusDays(1), null, null,
                DataSource.SMARTCAR_LIVE, null, ChargingType.DC,
                60000, 10, 90, null)
                .toBuilder().chargingProviderId(savedProvider.getId()).build();
        evLogRepository.save(previousLog);

        // 50 kWh / 1h = 50 kW → DC inferred → 50 × 0.79 + 1.00 = 40.50 EUR
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "50.0", 60, LocalDateTime.now().minusHours(1), geohash, "SMARTCAR_LIVE", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(0, new BigDecimal(body.get("costEur").toString()).compareTo(new BigDecimal("40.50")));
    }

    @Test
    void createLog_withExplicitCostEur_doesNotOverwriteWithProviderTariff() {
        UserChargingProviderEntity provider = new UserChargingProviderEntity();
        provider.setUserId(testUser.getId());
        provider.setProviderName("Heimladung");
        provider.setAcPricePerKwh(new BigDecimal("0.3900"));
        provider.setDcPricePerKwh(null);
        provider.setMonthlyFeeEur(BigDecimal.ZERO);
        provider.setSessionFeeEur(BigDecimal.ZERO);
        provider.setActiveFrom(LocalDate.now().minusDays(30));
        UserChargingProviderEntity savedProvider = chargingProviderRepository.save(provider);

        String geohash = "u2ey3d9";
        EvLog previousLog = EvLog.createFromInternal(
                testCar.getId(), new BigDecimal("50.0"), 120, geohash,
                LocalDateTime.now().minusDays(1), null, null,
                DataSource.SMARTCAR_LIVE, null, ChargingType.AC,
                60000, 20, 90, null)
                .toBuilder().chargingProviderId(savedProvider.getId()).build();
        evLogRepository.save(previousLog);

        // Explicit costEur provided → must NOT be overwritten
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "40.0", 120, LocalDateTime.now().minusHours(1), geohash, "SMARTCAR_LIVE", "9.99");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, new BigDecimal(response.getBody().get("costEur").toString()).compareTo(new BigDecimal("9.99")));
    }

    // --- Helpers ---

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> logRequest(UUID carId, UUID userId, String kwh,
                                            int durationMinutes, LocalDateTime loggedAt,
                                            String geohash, String dataSource, String costEur) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("carId", carId.toString());
        map.put("userId", userId.toString());
        map.put("kwhCharged", kwh);
        map.put("chargeDurationMinutes", durationMinutes);
        map.put("loggedAt", loggedAt.toString());
        map.put("geohash", geohash);
        map.put("dataSource", dataSource);
        map.put("costEur", costEur);
        return map;
    }
}
