package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.InternalEvLogRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for bidirectional duplicate suppression (superseded_by).
 *
 * Covers:
 * 1. USER_LOGGED after import → import gets superseded_by set, disappears from list
 * 2. Import after USER_LOGGED → import is immediately superseded
 * 3. Reversal: deleting the USER_LOGGED log restores the import (ON DELETE SET NULL)
 */
class EvLogDuplicateSuppressionIntegrationTest extends AbstractIntegrationTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private User testUser;
    private Car testCar;
    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("suppress-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
        testCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = testCar.getId();
    }

    /**
     * Test 1: Tesla import already exists → user manually logs same session
     * Expected: import gets suppressed, only user log appears in list
     */
    @Test
    void userLog_afterTeslaImport_suppressesImport() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(2);
        BigDecimal kwh = new BigDecimal("45.0");

        // Given: Tesla import already in DB
        EvLog teslaImport = EvLog.createNewWithSource(
                carId, kwh, null, 60, null, null, null, null, sessionTime,
                DataSource.TESLA_FLEET_IMPORT, ChargingType.DC, null);
        EvLog savedImport = evLogRepository.save(teslaImport);
        UUID importId = savedImport.getId();

        // When: User manually logs the same session (within 15 min window, same kWh)
        EvLogRequest request = new EvLogRequest(
                carId, kwh, new BigDecimal("12.00"), 60,
                null, null, 50000, null, 80,
                sessionTime.plusMinutes(3),
                null, null, null, null);

        ResponseEntity<EvLogCreateResponse> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(request, userId, testUser.getEmail()),
                EvLogCreateResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        UUID userLogId = response.getBody().log().id();

        // Then: import has superseded_by = userLogId in DB
        EvLog importAfter = evLogRepository.findById(importId).orElseThrow();
        assertEquals(userLogId, importAfter.getSupersededBy(),
                "Import log must be marked as superseded by the user log");

        // And: import no longer appears in the log list
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/logs?carId=" + carId,
                HttpMethod.GET,
                createAuthRequest(userId, testUser.getEmail()),
                List.class);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertEquals(1, listResponse.getBody().size(),
                "Only the user log should appear — import must be hidden");
    }

    /**
     * Test 2: User log already exists → Tesla import arrives for same session
     * Expected: import is immediately marked as superseded, never appears in list
     */
    @Test
    void teslaImport_afterUserLog_isImmediatelySuperseded() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(3);
        BigDecimal kwh = new BigDecimal("38.5");

        // Given: User log already exists
        EvLogRequest userRequest = new EvLogRequest(
                carId, kwh, new BigDecimal("10.00"), 55,
                null, null, 48000, null, 75,
                sessionTime,
                null, null, null, null);

        ResponseEntity<EvLogCreateResponse> userResponse = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(userRequest, userId, testUser.getEmail()),
                EvLogCreateResponse.class);

        assertEquals(HttpStatus.CREATED, userResponse.getStatusCode());
        UUID userLogId = userResponse.getBody().log().id();

        // When: Tesla import arrives for the same session (within window)
        InternalEvLogRequest importRequest = new InternalEvLogRequest(
                carId, userId, kwh, 55,
                sessionTime.plusMinutes(5),
                null, null, null,
                "TESLA_FLEET_IMPORT", null, "DC", false,
                null, null, null, null, null);

        HttpHeaders internalHeaders = new HttpHeaders();
        internalHeaders.set("X-Internal-Token", INTERNAL_TOKEN);
        ResponseEntity<EvLogResponse> importResponse = restTemplate.exchange(
                "/api/internal/logs",
                HttpMethod.POST,
                new HttpEntity<>(importRequest, internalHeaders),
                EvLogResponse.class);

        assertEquals(HttpStatus.OK, importResponse.getStatusCode());
        UUID importId = importResponse.getBody().id();

        // Then: import is superseded immediately
        EvLog importAfter = evLogRepository.findById(importId).orElseThrow();
        assertEquals(userLogId, importAfter.getSupersededBy(),
                "Import must be marked as superseded by the pre-existing user log");

        // And: list still shows only 1 log (user log)
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/logs?carId=" + carId,
                HttpMethod.GET,
                createAuthRequest(userId, testUser.getEmail()),
                List.class);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(1, listResponse.getBody().size(),
                "Import must be hidden from list immediately after creation");
    }

    /**
     * Test 3: Reversal — deleting the user log restores the import (ON DELETE SET NULL)
     */
    @Test
    void deletingUserLog_restoresImport() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(4);
        BigDecimal kwh = new BigDecimal("52.0");

        // Given: Tesla import exists
        EvLog teslaImport = EvLog.createNewWithSource(
                carId, kwh, null, 70, null, null, null, null, sessionTime,
                DataSource.TESLA_FLEET_IMPORT, ChargingType.DC, null);
        EvLog savedImport = evLogRepository.save(teslaImport);
        UUID importId = savedImport.getId();

        // And: user log suppresses it
        EvLogRequest request = new EvLogRequest(
                carId, kwh, new BigDecimal("15.00"), 70,
                null, null, 55000, null, 85,
                sessionTime.plusMinutes(2),
                null, null, null, null);

        ResponseEntity<EvLogCreateResponse> createResponse = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(request, userId, testUser.getEmail()),
                EvLogCreateResponse.class);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        UUID userLogId = createResponse.getBody().log().id();

        // Verify import is suppressed
        assertEquals(userLogId, evLogRepository.findById(importId).orElseThrow().getSupersededBy());

        // When: user deletes their log
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/logs/" + userLogId,
                HttpMethod.DELETE,
                createAuthRequest(userId, testUser.getEmail()),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Then: import is restored (superseded_by = NULL via ON DELETE SET NULL)
        EvLog importAfter = evLogRepository.findById(importId).orElseThrow();
        assertNull(importAfter.getSupersededBy(),
                "Import must be restored (superseded_by = null) after user log is deleted");

        // And: import appears in the list again
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/logs?carId=" + carId,
                HttpMethod.GET,
                createAuthRequest(userId, testUser.getEmail()),
                List.class);

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(1, listResponse.getBody().size(),
                "Import must reappear in list after user log is deleted");
    }
}
