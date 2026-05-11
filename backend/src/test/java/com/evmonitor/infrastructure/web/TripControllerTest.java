package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TripControllerTest extends AbstractIntegrationTest {

    @Autowired
    EvTripRepository tripRepository;

    private User user1;
    private User user2;
    private Car car1;
    private Car car2;

    @BeforeEach
    void setUp() {
        long ts = System.nanoTime();
        // AutoSync Live (Tier 2) - unlocks manual trip creation + live-trip CRUD.
        user1 = createAndSaveAutoSyncLiveUser("trip-ctrl-u1-" + ts + "@example.com");
        user2 = createAndSaveAutoSyncLiveUser("trip-ctrl-u2-" + ts + "@example.com");
        car1  = createAndSaveCar(user1.getId(), CarBrand.CarModel.MODEL_3);
        car2  = createAndSaveCar(user2.getId(), CarBrand.CarModel.MODEL_Y);
    }

    // --- POST /api/trips ---

    @Test
    void createTrip_validRequest_returns201WithId() {
        Map<String, Object> req = createTripRequest(car1.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now());

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertNotNull(res.getBody().get("id"));
        assertEquals("TRIP", res.getBody().get("type"));
        assertEquals(87.5, ((Number) res.getBody().get("distanceKm")).doubleValue(), 0.01);
        assertEquals("HIGHWAY", res.getBody().get("routeType"));
        assertEquals("USER_CREATED", res.getBody().get("dataSource"));
    }

    @Test
    void createTrip_carBelongsToOtherUser_returns404() {
        Map<String, Object> req = createTripRequest(car2.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now());

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void createTrip_unauthenticated_returns401or403() {
        Map<String, Object> req = createTripRequest(car1.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now());

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req), String.class);

        assertTrue(res.getStatusCode() == HttpStatus.UNAUTHORIZED || res.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    void createTrip_nonPremiumUser_returns403() {
        User freeUser = createAndSaveUser("trip-ctrl-free-" + System.nanoTime() + "@example.com");
        Map<String, Object> req = createTripRequest(car1.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now());

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void createTrip_tier1AutoSyncUser_returns403() {
        // AUTOSYNC (Tier 1) does NOT unlock manual trip creation - that is a Tier-2 feature.
        User tier1 = createAndSavePremiumUser("trip-ctrl-tier1-" + System.nanoTime() + "@example.com");
        Map<String, Object> req = createTripRequest(car1.getId(), OffsetDateTime.now().minusHours(2), OffsetDateTime.now());

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(tier1.getId(), tier1.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    // --- PATCH /api/trips/{id} ---

    @Test
    void updateTrip_validRequest_returns200WithUpdatedFieldsAndUserEditedAt() {
        EvTrip existing = saveTrip(user1.getId(), car1.getId(), false);

        Map<String, Object> patch = Map.of(
                "distanceKm", 123.0,
                "routeType", "CITY"
        );

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + existing.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(123.0, ((Number) res.getBody().get("distanceKm")).doubleValue(), 0.01);
        assertEquals("CITY", res.getBody().get("routeType"));

        EvTrip updated = tripRepository.findById(existing.getId()).orElseThrow();
        assertNotNull(updated.getUserEditedAt());
    }

    @Test
    void updateTrip_otherUsersTrip_returns404() {
        EvTrip trip = saveTrip(user2.getId(), car2.getId(), false);

        Map<String, Object> patch = Map.of("distanceKm", 50.0);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    // --- DELETE /api/trips/{id} ---

    @Test
    void deleteTrip_validRequest_returns204AndTripHasDeletedAt() {
        EvTrip trip = saveTrip(user1.getId(), car1.getId(), false);

        ResponseEntity<Void> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        // Soft delete: row still exists in DB but deletedAt is set
        EvTrip deleted = tripRepository.findById(trip.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void deleteTrip_deletedTripDoesNotAppearInGetTrips() {
        EvTrip trip = saveTrip(user1.getId(), car1.getId(), false);

        restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Void.class);

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + car1.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(0, res.getBody().length);
    }

    @Test
    void deleteTrip_otherUsersTrip_returns404() {
        EvTrip trip = saveTrip(user2.getId(), car2.getId(), false);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        // Row must still exist and must NOT be soft-deleted
        EvTrip untouched = tripRepository.findById(trip.getId()).orElseThrow();
        assertNull(untouched.getDeletedAt());
    }

    // --- GET /api/trips?carId=X ---

    @Test
    void getTrips_returnsOnlyTripsForOwnedCar() {
        saveTrip(user1.getId(), car1.getId(), false);
        saveTrip(user1.getId(), car1.getId(), true);
        saveTrip(user2.getId(), car2.getId(), false);

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + car1.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(2, res.getBody().length);
    }

    @Test
    void getTrips_carBelongsToOtherUser_returns404() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips?carId=" + car2.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void updateTrip_feedback_persistedWithoutSettingUserEditedAt() {
        EvTrip existing = saveTrip(user1.getId(), car1.getId(), false);

        Map<String, Object> patch = Map.of("feedback", "negative | falsche-distanz | war zu kurz");

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + existing.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("negative | falsche-distanz | war zu kurz", res.getBody().get("feedback"));

        EvTrip updated = tripRepository.findById(existing.getId()).orElseThrow();
        assertEquals("negative | falsche-distanz | war zu kurz", updated.getFeedback());
        assertNull(updated.getUserEditedAt()); // feedback is not a data edit
    }

    // --- validation ---

    @Test
    void createTrip_invalidRouteType_returns400() {
        Map<String, Object> req = Map.of(
                "carId", car1.getId().toString(),
                "tripStartedAt", OffsetDateTime.now().minusHours(2).toString(),
                "tripEndedAt", OffsetDateTime.now().toString(),
                "routeType", "MOTORWAY"
        );

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void updateTrip_invalidRouteType_returns400() {
        EvTrip existing = saveTrip(user1.getId(), car1.getId(), false);

        Map<String, Object> patch = Map.of("routeType", "MOTORWAY");

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + existing.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void createTrip_startAfterEnd_returns404() {
        Map<String, Object> req = Map.of(
                "carId", car1.getId().toString(),
                "tripStartedAt", OffsetDateTime.now().toString(),
                "tripEndedAt", OffsetDateTime.now().minusHours(1).toString()
        );

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips", HttpMethod.POST,
                new HttpEntity<>(req, createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    // --- POST /api/trips/{id}/merge ---

    @Test
    void mergeTrip_happyPath_returns200WithMergedData() {
        EvTrip previous = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                new BigDecimal("80.0"), new BigDecimal("60.0"), new BigDecimal("20.0"), "HIGHWAY");
        EvTrip active = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                new BigDecimal("58.0"), new BigDecimal("40.0"), new BigDecimal("15.0"), "HIGHWAY");

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + active.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", previous.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        // distance = sum
        assertEquals(35.0, ((Number) body.get("distanceKm")).doubleValue(), 0.01);
        // soc_start from earlier trip
        assertEquals(80.0, ((Number) body.get("socStart")).doubleValue(), 0.01);
        // soc_end from later trip
        assertEquals(40.0, ((Number) body.get("socEnd")).doubleValue(), 0.01);
        // route_type same -> kept
        assertEquals("HIGHWAY", body.get("routeType"));
        // previous trip soft-deleted
        assertNotNull(tripRepository.findById(previous.getId()).orElseThrow().getDeletedAt());
        // active trip still exists
        assertNull(tripRepository.findById(active.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void mergeTrip_differentRouteTypes_setsCombined() {
        EvTrip previous = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("20.0"), "CITY");
        EvTrip active = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("15.0"), "HIGHWAY");

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + active.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", previous.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("COMBINED", res.getBody().get("routeType"));
    }

    @Test
    void mergeTrip_tripsBelongToDifferentCars_returns400() {
        EvTrip fromCar1 = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("20.0"), null);
        EvTrip fromCar2 = saveTripFull(user1.getId(), car2.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("15.0"), null);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + fromCar2.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", fromCar1.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void mergeTrip_mergeWithTripBelongsToOtherUser_returns404() {
        EvTrip other = saveTripFull(user2.getId(), car2.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("20.0"), null);
        EvTrip active = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("15.0"), null);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + active.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", other.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void mergeTrip_smallSocDelta_estimatedConsumedKwhCalculatedFromSpec() {
        // Simulate Tesla (66.94%) + Smartcar (66%) SoC source noise - delta < 2%
        EvTrip previous = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                new BigDecimal("66.94"), new BigDecimal("66.0"), new BigDecimal("20.0"), "COMBINED");
        EvTrip active = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now(),
                new BigDecimal("65.0"), new BigDecimal("64.0"), new BigDecimal("15.0"), "COMBINED");

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + active.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", previous.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        // merged soc_start=66.94, soc_end=64.0 → delta=2.94 ≥ 2.0 → calculation runs
        // this trip uses correct soc across both trips, so NOT null
        assertNotNull(res.getBody().get("estimatedConsumedKwh"));

        // now test the actual problematic scenario: tiny delta across merged result
        EvTrip t1 = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                new BigDecimal("66.94"), null, new BigDecimal("10.0"), null);
        EvTrip t2 = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, new BigDecimal("66.0"), new BigDecimal("8.0"), null);

        ResponseEntity<Map<String, Object>> res2 = restTemplate.exchange(
                "/api/trips/" + t2.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", t1.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res2.getStatusCode());
        // merged soc_start=66.94, soc_end=66.0 → delta=0.94 > 0 → spec-based calculation runs (no 2% guard)
        assertNotNull(res2.getBody().get("estimatedConsumedKwh"));
    }

    @Test
    void mergeTrip_mergeWithItself_returns400() {
        EvTrip trip = saveTripFull(user1.getId(), car1.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("20.0"), null);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + trip.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", trip.getId().toString()),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    // --- Tessie-import policy: imported trips (data_source=TESSIE) stay editable for the owner ---
    // Background: Tessie import lets free users bring their own historical trip data into
    // ev-monitor. Owner CRUD on TESSIE trips works without any subscription. Live-detected
    // trips (SMARTCAR_LIVE, TESLA_LIVE, TESLA_INFERRED) are gated behind AutoSync Live.

    @Test
    void mergeTrip_nonPremiumUserOnTessieTrips_returns200() {
        User freeUser = createAndSaveUser("merge-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip t1 = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "TESSIE");
        EvTrip t2 = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "TESSIE");

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + t2.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", t1.getId().toString()),
                        createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(20.0, ((Number) res.getBody().get("distanceKm")).doubleValue(), 0.01);
    }

    @Test
    void getTrips_nonPremiumUser_returnsOnlyTessieTrips() {
        User freeUser = createAndSaveUser("get-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        // free user shouldn't have live trips, but if they exist (e.g. user downgraded)
        // they must NOT show up in the response.
        saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "TESSIE");
        saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("12.0"), null, "TESLA_LIVE");

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + freeCar.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().length);
    }

    @Test
    void updateTrip_nonPremiumUserOnTessieTrip_returns200() {
        User freeUser = createAndSaveUser("patch-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip trip = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "TESSIE");

        Map<String, Object> patch = Map.of("distanceKm", 99.0);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(99.0, ((Number) res.getBody().get("distanceKm")).doubleValue(), 0.01);
    }

    @Test
    void deleteTrip_nonPremiumUserOnTessieTrip_returns204() {
        User freeUser = createAndSaveUser("delete-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip trip = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "TESSIE");

        ResponseEntity<Void> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        assertNotNull(tripRepository.findById(trip.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void updateTrip_nonPremiumUser_otherUsersTrip_returns404() {
        User freeUser = createAndSaveUser("patch-cross-free-" + System.nanoTime() + "@example.com");
        EvTrip foreignTrip = saveTrip(user2.getId(), car2.getId(), false);

        Map<String, Object> patch = Map.of("distanceKm", 99.0);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + foreignTrip.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patch, createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNull(tripRepository.findById(foreignTrip.getId()).orElseThrow().getUserEditedAt());
    }

    @Test
    void deleteTrip_nonPremiumUser_otherUsersTrip_returns404() {
        User freeUser = createAndSaveUser("delete-cross-free-" + System.nanoTime() + "@example.com");
        EvTrip foreignTrip = saveTrip(user2.getId(), car2.getId(), false);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + foreignTrip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNull(tripRepository.findById(foreignTrip.getId()).orElseThrow().getDeletedAt());
    }

    // --- Live-trip gating: non-Tier-2 users (free + AUTOSYNC) cannot CRUD live-detected trips ---

    @Test
    void getTrips_tier1AutoSyncUser_hidesLiveTrips() {
        User tier1 = createAndSavePremiumUser("get-tier1-" + System.nanoTime() + "@example.com");
        Car tier1Car = createAndSaveCar(tier1.getId(), CarBrand.CarModel.MODEL_3);
        saveTripWithSource(tier1.getId(), tier1Car.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "TESLA_LIVE");
        saveTripWithSource(tier1.getId(), tier1Car.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("12.0"), null, "TESSIE");

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + tier1Car.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(tier1.getId(), tier1.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().length);
    }

    @Test
    void updateTrip_nonPremiumUserOnOwnLiveTrip_returns404() {
        User freeUser = createAndSaveUser("patch-live-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip liveTrip = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "SMARTCAR_LIVE");

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + liveTrip.getId(), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("distanceKm", 99.0),
                        createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNull(tripRepository.findById(liveTrip.getId()).orElseThrow().getUserEditedAt());
    }

    @Test
    void deleteTrip_nonPremiumUserOnOwnLiveTrip_returns404() {
        User freeUser = createAndSaveUser("del-live-free-" + System.nanoTime() + "@example.com");
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip liveTrip = saveTripWithSource(freeUser.getId(), freeCar.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "TESLA_LIVE");

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + liveTrip.getId(), HttpMethod.DELETE,
                new HttpEntity<>(createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNull(tripRepository.findById(liveTrip.getId()).orElseThrow().getDeletedAt());
    }

    // --- Car-model eligibility: live trips on BLOCKED models stay hidden for regular users ---

    @Test
    void getTrips_autoSyncLiveUser_onBlockedModel_hidesLiveTrips() {
        // Polestar 3 is on the BLOCKED list (integer-only data, too coarse cadence).
        // The user pays for AutoSync Live but does not see live trips for this car.
        // The data IS collected in the DB - just filtered out of the read path. The
        // Tessie trip from the same car remains visible.
        Car p3 = createAndSaveCar(user1.getId(), CarBrand.CarModel.POLESTAR_3);
        saveTripWithSource(user1.getId(), p3.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "SMARTCAR_LIVE");
        saveTripWithSource(user1.getId(), p3.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("12.0"), null, "TESSIE");

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + p3.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().length);
    }

    @Test
    void getTrips_autoSyncLiveUser_onEligibleModel_seesLiveTrips() {
        // Polestar 2 is on the ELIGIBLE whitelist - live trips show up as expected.
        Car p2 = createAndSaveCar(user1.getId(), CarBrand.CarModel.POLESTAR_2);
        saveTripWithSource(user1.getId(), p2.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "SMARTCAR_LIVE");

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + p2.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(user1.getId(), user1.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().length);
    }

    @Test
    void updateTrip_autoSyncLiveUser_onBlockedModel_returns404() {
        // PATCH on a live trip of a BLOCKED model is rejected like any other gated trip.
        Car p3 = createAndSaveCar(user1.getId(), CarBrand.CarModel.POLESTAR_3);
        EvTrip trip = saveTripWithSource(user1.getId(), p3.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "SMARTCAR_LIVE");

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + trip.getId(), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("distanceKm", 99.0),
                        createAuthHeaders(user1.getId(), user1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    void getTrips_adminUser_onBlockedModel_seesLiveTrips() {
        // ADMIN bypasses the eligibility gate for data-quality audit by impersonation.
        // Same logic applies to BETA_TESTER.
        User admin = createAndSaveAdminUser("trip-admin-" + System.nanoTime() + "@example.com");
        Car p3 = createAndSaveCar(admin.getId(), CarBrand.CarModel.POLESTAR_3);
        saveTripWithSource(admin.getId(), p3.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "SMARTCAR_LIVE");

        ResponseEntity<Object[]> res = restTemplate.exchange(
                "/api/trips?carId=" + p3.getId(), HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(admin.getId(), admin.getEmail())),
                Object[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().length);
    }

    @Test
    void mergeTrip_tier1UserOnLiveTrips_returns404() {
        User tier1 = createAndSavePremiumUser("merge-tier1-" + System.nanoTime() + "@example.com");
        Car tier1Car = createAndSaveCar(tier1.getId(), CarBrand.CarModel.MODEL_3);
        EvTrip t1 = saveTripWithSource(tier1.getId(), tier1Car.getId(),
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(4),
                null, null, new BigDecimal("10.0"), null, "TESLA_LIVE");
        EvTrip t2 = saveTripWithSource(tier1.getId(), tier1Car.getId(),
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2),
                null, null, new BigDecimal("10.0"), null, "TESLA_LIVE");

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/trips/" + t2.getId() + "/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("mergeWithTripId", t1.getId().toString()),
                        createAuthHeaders(tier1.getId(), tier1.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    // --- helpers ---

    private Map<String, Object> createTripRequest(UUID carId, OffsetDateTime start, OffsetDateTime end) {
        return Map.of(
                "carId", carId.toString(),
                "tripStartedAt", start.toString(),
                "tripEndedAt", end.toString(),
                "distanceKm", 87.5,
                "routeType", "HIGHWAY"
        );
    }

    private EvTrip saveTrip(UUID userId, UUID carId, boolean userCreated) {
        EvTrip trip = EvTrip.builder()
                .userId(userId)
                .carId(carId)
                .dataSource(userCreated ? "USER_CREATED" : "TESLA_LIVE")
                .tripStartedAt(OffsetDateTime.now().minusHours(3))
                .tripEndedAt(OffsetDateTime.now().minusHours(1))
                .distanceKm(new BigDecimal("55.0"))
                .status("COMPLETED")
                .userCreated(userCreated)
                .build();
        return tripRepository.save(trip);
    }

    private EvTrip saveTripFull(UUID userId, UUID carId,
            OffsetDateTime start, OffsetDateTime end,
            BigDecimal socStart, BigDecimal socEnd,
            BigDecimal distanceKm, String routeType) {
        return saveTripWithSource(userId, carId, start, end, socStart, socEnd, distanceKm, routeType, "TESLA_LIVE");
    }

    private EvTrip saveTripWithSource(UUID userId, UUID carId,
            OffsetDateTime start, OffsetDateTime end,
            BigDecimal socStart, BigDecimal socEnd,
            BigDecimal distanceKm, String routeType, String dataSource) {
        EvTrip trip = EvTrip.builder()
                .userId(userId)
                .carId(carId)
                .dataSource(dataSource)
                .tripStartedAt(start)
                .tripEndedAt(end)
                .socStart(socStart)
                .socEnd(socEnd)
                .distanceKm(distanceKm)
                .routeType(routeType)
                .status("COMPLETED")
                .userCreated(false)
                .build();
        return tripRepository.save(trip);
    }
}
