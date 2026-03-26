package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkRepository;
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
 * Integration tests for PATCH /api/v1/sessions/{id}.
 *
 * Covers: partial update, field preservation, ownership, data_source restriction, auth, CPO normalization.
 */
class PublicApiPatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private JpaChargingNetworkRepository networkRepository;

    @Autowired
    private CpoNameNormalizer cpoNameNormalizer;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("api-patch-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Test Key");
        plaintextKey = created.plaintextKey();

        // Seed CPO test data and reload normalizer (Flyway disabled in tests)
        networkRepository.save(entity("IONITY", null));
        networkRepository.save(entity("Fastned", null));
        networkRepository.save(entity("EnBW", "DE"));
        cpoNameNormalizer.init();
    }

    private ChargingNetworkEntity entity(String name, String countryCode) {
        ChargingNetworkEntity e = new ChargingNetworkEntity();
        e.setName(name);
        e.setCountryCode(countryCode);
        return e;
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void patch_updatesProvidedFields() {
        EvLog log = createApiUploadLog(car.getId());

        String body = """
                { "is_public_charging": true, "cpo_name": "IONITY", "cost_eur": 12.50 }
                """;

        ResponseEntity<Void> response = patch(log.getId(), body, plaintextKey);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        EvLog updated = evLogRepository.findById(log.getId()).orElseThrow();
        assertTrue(updated.isPublicCharging());
        assertEquals("IONITY", updated.getCpoName());
        assertEquals(0, BigDecimal.valueOf(12.50).compareTo(updated.getCostEur()));
    }

    @Test
    void patch_omittedFields_remainUnchanged() {
        EvLog log = createApiUploadLog(car.getId());
        BigDecimal originalKwh = log.getKwhCharged();

        String body = """
                { "cpo_name": "EnBW" }
                """;

        patch(log.getId(), body, plaintextKey);

        EvLog updated = evLogRepository.findById(log.getId()).orElseThrow();
        assertEquals("EnBW", updated.getCpoName());
        assertEquals(0, originalKwh.compareTo(updated.getKwhCharged())); // kwh unchanged
        assertEquals(log.getOdometerKm(), updated.getOdometerKm());       // odometer unchanged
    }

    @Test
    void patch_cpoName_normalizedToCanonical() {
        EvLog log = createApiUploadLog(car.getId());

        patch(log.getId(), """
                { "is_public_charging": true, "cpo_name": "fastned" }
                """, plaintextKey);

        EvLog updated = evLogRepository.findById(log.getId()).orElseThrow();
        assertEquals("Fastned", updated.getCpoName());
    }

    @Test
    void patch_location_recomputesGeohash() {
        EvLog log = createApiUploadLog(car.getId());

        patch(log.getId(), """
                { "location": "48.1234,11.5678", "is_public_charging": true }
                """, plaintextKey);

        EvLog updated = evLogRepository.findById(log.getId()).orElseThrow();
        assertNotNull(updated.getGeohash());
        assertEquals(7, updated.getGeohash().length()); // public → 7 chars
    }

    // ── Restrictions ──────────────────────────────────────────────────────────

    @Test
    void patch_nonApiUploadLog_returns400() {
        EvLog userLog = EvLog.createNew(car.getId(), BigDecimal.valueOf(20.0), null,
                30, null, null, null, 80, LocalDateTime.now(),
                ChargingType.AC, null, null);
        EvLog saved = evLogRepository.save(userLog);

        ResponseEntity<Map> response = patchForMap(saved.getId(), """
                { "cpo_name": "IONITY" }
                """, plaintextKey);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void patch_foreignLog_returns403() {
        User other = createAndSaveUser("other-patch-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog otherLog = createApiUploadLog(otherCar.getId());

        ResponseEntity<Map> response = patchForMap(otherLog.getId(), """
                { "cpo_name": "IONITY" }
                """, plaintextKey);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void patch_unknownId_returns404() {
        ResponseEntity<Map> response = patchForMap(UUID.randomUUID(), """
                { "cpo_name": "IONITY" }
                """, plaintextKey);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void patch_noApiKey_returns401() {
        EvLog log = createApiUploadLog(car.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions/" + log.getId(), HttpMethod.PATCH,
                new HttpEntity<>("{}", headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvLog createApiUploadLog(UUID carId) {
        EvLog log = EvLog.createFromPublicApi(
                carId,
                BigDecimal.valueOf(30.0), BigDecimal.valueOf(8.50),
                40, null, 50000,
                BigDecimal.valueOf(11.0), 85, 20,
                LocalDateTime.now().minusHours(1),
                ChargingType.AC, RouteType.COMBINED, TireType.SUMMER,
                DataSource.API_UPLOAD, null);
        return evLogRepository.save(log);
    }

    private ResponseEntity<Void> patch(UUID id, String jsonBody, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/sessions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(jsonBody, headers), Void.class);
    }

    private ResponseEntity<Map> patchForMap(UUID id, String jsonBody, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/sessions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(jsonBody, headers), Map.class);
    }
}
