package com.evmonitor.infrastructure.web;

import com.evmonitor.application.LiveChargingHistoryResponse;
import com.evmonitor.application.LiveChargingResponse;
import com.evmonitor.application.LiveChargingService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for LiveController.
 *
 * LiveChargingService (the connectors-service proxy) is MockBean'd to isolate
 * the controller logic (auth/ownership/tier gate) from the actual HTTP call.
 */
class LiveControllerTest extends AbstractIntegrationTest {

    @MockBean
    private LiveChargingService liveChargingService;

    private User liveUser;
    private User tier1User;
    private User freeUser;
    private User otherUser;
    private Car liveCar;
    private Car otherCar;

    @BeforeEach
    void setUp() {
        long ts = System.nanoTime();
        liveUser  = createAndSaveAutoSyncLiveUser("live-ctrl-live-"  + ts + "@example.com");
        tier1User = createAndSavePremiumUser(      "live-ctrl-tier1-" + ts + "@example.com");
        freeUser  = createAndSaveUser(             "live-ctrl-free-"  + ts + "@example.com");
        otherUser = createAndSaveAutoSyncLiveUser( "live-ctrl-other-" + ts + "@example.com");
        liveCar   = createAndSaveCar(liveUser.getId(),  CarBrand.CarModel.MODEL_3);
        otherCar  = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.MODEL_Y);
    }

    // --- 200: AUTOSYNC_LIVE user, owns car, active session ---

    @Test
    void getLiveCharging_autoSyncLiveUser_activeSession_returns200() {
        LiveChargingResponse active = new LiveChargingResponse(
                true, "DC",
                new BigDecimal("100.0"),
                new BigDecimal("72.5"),
                new BigDecimal("18.3"),
                25,
                new BigDecimal("210.0"),
                new BigDecimal("200.0"),
                LocalDateTime.now().minusMinutes(10),
                new BigDecimal("40.0"),
                80,
                LocalDateTime.now());
        when(liveChargingService.getLiveCharging(liveCar.getId())).thenReturn(active);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        // Java record boolean field "isActive" serializes as "isActive" in JSON
        assertEquals(true, res.getBody().get("isActive"));
        assertEquals("DC", res.getBody().get("chargingType"));
    }

    // --- 200: ADMIN user, active session ---

    @Test
    void getLiveCharging_adminUser_returns200() {
        User admin = createAndSaveAdminUser("live-ctrl-admin-" + System.nanoTime() + "@example.com");
        Car adminCar = createAndSaveCar(admin.getId(), CarBrand.CarModel.MODEL_3);
        when(liveChargingService.getLiveCharging(adminCar.getId()))
                .thenReturn(LiveChargingResponse.inactive());

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + adminCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(admin.getId(), admin.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    // --- 200 with isActive=false when connectors returns no session ---

    @Test
    void getLiveCharging_noActiveSession_returns200WithIsActiveFalse() {
        when(liveChargingService.getLiveCharging(liveCar.getId()))
                .thenReturn(LiveChargingResponse.inactive());

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(false, res.getBody().get("isActive"));
    }

    // --- 200 with isActive=false when connectors call fails (connection refused / 404) ---

    @Test
    void getLiveCharging_connectorsUnavailable_returns200WithIsActiveFalse() {
        // LiveChargingService already swallows exceptions and returns inactive()
        when(liveChargingService.getLiveCharging(liveCar.getId()))
                .thenReturn(LiveChargingResponse.inactive());

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(false, res.getBody().get("isActive"));
    }

    // --- 403: AUTOSYNC (Tier 1) user ---

    @Test
    void getLiveCharging_tier1AutoSyncUser_returns403() {
        Car tier1Car = createAndSaveCar(tier1User.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + tier1Car.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(tier1User.getId(), tier1User.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    // --- 403: free user ---

    @Test
    void getLiveCharging_freeUser_returns403() {
        Car freeCar = createAndSaveCar(freeUser.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + freeCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(freeUser.getId(), freeUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    // --- 403: user does not own the car ---

    @Test
    void getLiveCharging_nonOwner_returns403() {
        // liveUser (AUTOSYNC_LIVE) tries to access otherUser's car
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + otherCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    // --- 401/403: unauthenticated ---

    @Test
    void getLiveCharging_unauthenticated_returns401or403() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(null, null),
                String.class);

        assertTrue(
                res.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                res.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + res.getStatusCode());
    }

    // --- 404: car does not exist ---

    @Test
    void getLiveCharging_carNotFound_returns404() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + java.util.UUID.randomUUID() + "/live/charging",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    // ── /live/charging/history ──────────────────────────────────────────────

    @Test
    void getLiveChargingHistory_autoSyncLiveUser_returns200WithPoints() {
        LiveChargingHistoryResponse history = new LiveChargingHistoryResponse(List.of(
                new LiveChargingHistoryResponse.Point(Instant.parse("2026-05-12T10:00:00Z"), 11.0),
                new LiveChargingHistoryResponse.Point(Instant.parse("2026-05-12T10:00:05Z"), 150.0)));
        when(liveChargingService.getLiveChargingHistory(eq(liveCar.getId()), anyInt()))
                .thenReturn(history);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging/history",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) res.getBody().get("points");
        assertNotNull(points);
        assertEquals(2, points.size());
    }

    @Test
    void getLiveChargingHistory_tier1AutoSyncUser_returns403() {
        Car tier1Car = createAndSaveCar(tier1User.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + tier1Car.getId() + "/live/charging/history",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(tier1User.getId(), tier1User.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void getLiveChargingHistory_nonOwner_returns403() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + otherCar.getId() + "/live/charging/history",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void getLiveChargingHistory_connectorsUnavailable_returns200WithEmptyPoints() {
        when(liveChargingService.getLiveChargingHistory(eq(liveCar.getId()), anyInt()))
                .thenReturn(LiveChargingHistoryResponse.empty());

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/cars/" + liveCar.getId() + "/live/charging/history",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) res.getBody().get("points");
        assertNotNull(points);
        assertEquals(0, points.size());
    }

    @Test
    void getLiveChargingHistory_carNotFound_returns404() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/cars/" + java.util.UUID.randomUUID() + "/live/charging/history",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(liveUser.getId(), liveUser.getEmail())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }
}
