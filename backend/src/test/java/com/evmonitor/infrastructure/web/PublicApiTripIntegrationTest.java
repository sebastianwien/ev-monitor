package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET/POST/PATCH/DELETE /api/v1/trips.
 */
class PublicApiTripIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private EvTripRepository evTripRepository;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("trip-api-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Trip Test Key");
        plaintextKey = created.plaintextKey();
    }

    // ── POST /api/v1/trips ───────────────────────────────────────────────────

    @Test
    void createTrip_validRequest_returns201WithId() {
        Map<String, Object> body = Map.of(
                "car_id", car.getId().toString(),
                "started_at", "2025-06-01T08:00:00+02:00",
                "ended_at", "2025-06-01T09:00:00+02:00",
                "distance_km", 42.5,
                "soc_start", 80,
                "soc_end", 55,
                "route_type", "HIGHWAY"
        );

        ResponseEntity<Map> response = apiPost("/api/v1/trips", body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("id"));
        assertEquals(car.getId().toString(), response.getBody().get("car_id").toString());
        assertEquals("API_UPLOAD", response.getBody().get("data_source"));
    }

    @Test
    void createTrip_endBeforeStart_returns400() {
        Map<String, Object> body = Map.of(
                "car_id", car.getId().toString(),
                "started_at", "2025-06-01T10:00:00Z",
                "ended_at", "2025-06-01T09:00:00Z",
                "distance_km", 10.0
        );

        ResponseEntity<Map> response = apiPost("/api/v1/trips", body);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createTrip_foreignCar_returns403() {
        User other = createAndSaveUser("other-trip-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        Map<String, Object> body = Map.of(
                "car_id", otherCar.getId().toString(),
                "started_at", "2025-06-01T08:00:00Z",
                "ended_at", "2025-06-01T09:00:00Z",
                "distance_km", 10.0
        );

        ResponseEntity<Map> response = apiPost("/api/v1/trips", body);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createTrip_noApiKey_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/trips", HttpMethod.POST,
                new HttpEntity<>(Map.of()), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/v1/trips ────────────────────────────────────────────────────

    @Test
    void listTrips_returnsPagedResult() {
        evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.of(2025, 6, 1, 8, 0, 0, 0, ZoneOffset.UTC)));
        evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.of(2025, 6, 2, 8, 0, 0, 0, ZoneOffset.UTC)));

        ResponseEntity<Map> response = apiGet("/api/v1/trips?car_id=" + car.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("trips"));
        assertNotNull(response.getBody().get("total"));
    }

    @Test
    void listTrips_invalidDateFormat_returns400() {
        ResponseEntity<Map> response = apiGet("/api/v1/trips?car_id=" + car.getId() + "&from=01.06.2025");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("error"));
    }

    @Test
    void listTrips_foreignCar_returns403() {
        User other = createAndSaveUser("other-list-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<Map> response = apiGet("/api/v1/trips?car_id=" + otherCar.getId());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listTrips_dateFilter_returnsMatchingTrips() {
        evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.of(2025, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC)));
        evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.of(2025, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC)));

        ResponseEntity<Map> response = apiGet("/api/v1/trips?car_id=" + car.getId() + "&from=2025-06-01&to=2025-06-30");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        // Only the June trip should be returned
        int total = ((Number) body.get("total")).intValue();
        assertEquals(1, total);
    }

    // ── GET /api/v1/trips/{id} ───────────────────────────────────────────────

    @Test
    void getTrip_ownedTrip_returnsFields() {
        EvTrip trip = evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.now().minusHours(2)));

        ResponseEntity<Map> response = apiGet("/api/v1/trips/" + trip.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(trip.getId().toString(), body.get("id").toString());
        assertEquals(car.getId().toString(), body.get("car_id").toString());
        assertNotNull(body.get("started_at"));
        assertNotNull(body.get("distance_km"));
    }

    /**
     * Speed and climate are deliberately NOT gated on the subscription tier here: the public
     * API hands a user their own raw per-trip data, whatever they pay. The paywall lives on
     * the aggregated analytics in the app, not on data portability.
     */
    @Test
    void getTrip_exposesSpeedsAndClimate_regardlessOfTier() {
        EvTrip trip = apiUploadTrip(car.getId(), user.getId(), OffsetDateTime.now().minusHours(2));
        trip.setAvgSpeedKmh(new BigDecimal("21.80"));
        trip.setMaxSpeedKmh(new BigDecimal("39.00"));
        trip.setTelemetryExtras("""
                {"tripSeconds":182,"climate":{
                  "comfortHeat":{"active":false,"seconds":0},
                  "hvacHeating":{"active":false,"seconds":0},
                  "hvacCooling":{"active":true,"seconds":85},
                  "batteryHeater":{"active":false,"seconds":0}}}""");
        trip = evTripRepository.save(trip);

        Map<?, ?> body = apiGet("/api/v1/trips/" + trip.getId()).getBody();

        assertNotNull(body);
        assertEquals(21.80, ((Number) body.get("avg_speed_kmh")).doubleValue(), 0.001);
        assertEquals(39.00, ((Number) body.get("max_speed_kmh")).doubleValue(), 0.001);
        Map<?, ?> climate = (Map<?, ?>) body.get("climate");
        assertNotNull(climate, "climate summary missing");
        assertEquals(182, ((Number) climate.get("trip_seconds")).intValue());
        Map<?, ?> cooling = (Map<?, ?>) climate.get("hvac_cooling");
        assertEquals(Boolean.TRUE, cooling.get("active"));
        assertEquals(85, ((Number) cooling.get("seconds")).intValue());
        assertEquals(Boolean.FALSE, ((Map<?, ?>) climate.get("hvac_heating")).get("active"));
    }

    /** No telemetry source, no climate block - clients must not see a hollow object. */
    @Test
    void getTrip_withoutTelemetryExtras_omitsClimate() {
        EvTrip trip = evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.now().minusHours(2)));

        Map<?, ?> body = apiGet("/api/v1/trips/" + trip.getId()).getBody();

        assertNotNull(body);
        assertNull(body.get("climate"));
    }

    @Test
    void getTrip_unknownId_returns404() {
        ResponseEntity<Map> response = apiGet("/api/v1/trips/" + UUID.randomUUID());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getTrip_foreignTrip_returns403() {
        User other = createAndSaveUser("other-get-trip-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip otherTrip = evTripRepository.save(apiUploadTrip(otherCar.getId(), other.getId(),
                OffsetDateTime.now().minusHours(1)));

        ResponseEntity<Map> response = apiGet("/api/v1/trips/" + otherTrip.getId());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PATCH /api/v1/trips/{id} ─────────────────────────────────────────────

    @Test
    void patchTrip_apiUploadTrip_updatesFields() {
        EvTrip trip = evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.now().minusHours(3)));

        Map<String, Object> patch = Map.of("distance_km", 99.9, "route_type", "CITY");
        ResponseEntity<Map> response = apiPatch("/api/v1/trips/" + trip.getId(), patch);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(99.9, ((Number) body.get("distance_km")).doubleValue(), 0.01);
        assertEquals("CITY", body.get("route_type"));
    }

    @Test
    void patchTrip_nonApiUploadTrip_succeeds() {
        EvTrip syncedTrip = EvTrip.builder()
                .carId(car.getId())
                .userId(user.getId())
                .dataSource("TESSIE")
                .tripStartedAt(OffsetDateTime.now().minusHours(3))
                .tripEndedAt(OffsetDateTime.now().minusHours(2))
                .status("COMPLETED")
                .build();
        syncedTrip = evTripRepository.save(syncedTrip);

        Map<String, Object> patch = Map.of("distance_km", 10.0);
        ResponseEntity<Map> response = apiPatch("/api/v1/trips/" + syncedTrip.getId(), patch);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void patchTrip_foreignTrip_returns403() {
        User other = createAndSaveUser("other-patch-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip otherTrip = evTripRepository.save(apiUploadTrip(otherCar.getId(), other.getId(),
                OffsetDateTime.now().minusHours(2)));

        Map<String, Object> patch = Map.of("distance_km", 5.0);
        ResponseEntity<Map> response = apiPatch("/api/v1/trips/" + otherTrip.getId(), patch);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── DELETE /api/v1/trips/{id} ────────────────────────────────────────────

    @Test
    void deleteTrip_apiUploadTrip_returns204() {
        EvTrip trip = evTripRepository.save(apiUploadTrip(car.getId(), user.getId(),
                OffsetDateTime.now().minusHours(1)));

        ResponseEntity<Void> response = apiDelete("/api/v1/trips/" + trip.getId());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify soft-deleted
        EvTrip deleted = evTripRepository.findById(trip.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void deleteTrip_nonApiUploadTrip_succeeds() {
        EvTrip syncedTrip = EvTrip.builder()
                .carId(car.getId())
                .userId(user.getId())
                .dataSource("TESSIE")
                .tripStartedAt(OffsetDateTime.now().minusHours(3))
                .tripEndedAt(OffsetDateTime.now().minusHours(2))
                .status("COMPLETED")
                .build();
        syncedTrip = evTripRepository.save(syncedTrip);

        ResponseEntity<Void> response = apiDelete("/api/v1/trips/" + syncedTrip.getId());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void deleteTrip_unknownId_returns404() {
        ResponseEntity<Void> response = apiDelete("/api/v1/trips/" + UUID.randomUUID());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvTrip apiUploadTrip(UUID carId, UUID userId, OffsetDateTime startedAt) {
        return EvTrip.builder()
                .carId(carId)
                .userId(userId)
                .dataSource(EvTrip.DATA_SOURCE_API_UPLOAD)
                .tripStartedAt(startedAt)
                .tripEndedAt(startedAt.plusHours(1))
                .distanceKm(BigDecimal.valueOf(42.0))
                .socStart(BigDecimal.valueOf(80))
                .socEnd(BigDecimal.valueOf(55))
                .routeType("HIGHWAY")
                .status("COMPLETED")
                .userCreated(true)
                .build();
    }

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> apiGet(String url) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(apiHeaders()), Map.class);
    }

    private ResponseEntity<Map> apiPost(String url, Object body) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, apiHeaders()), Map.class);
    }

    private ResponseEntity<Map> apiPatch(String url, Object body) {
        return restTemplate.exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(body, apiHeaders()), Map.class);
    }

    private ResponseEntity<Void> apiDelete(String url) {
        return restTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(apiHeaders()), Void.class);
    }
}
