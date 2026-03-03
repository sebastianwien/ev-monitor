package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for EvLogController.
 * Tests charging log CRUD operations and statistics API with full Spring context.
 *
 * SECURITY CRITICAL: Users must only access their own logs!
 * PRIVACY CRITICAL: Geohashing must work end-to-end!
 */
class EvLogControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private Car testCar;
    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUpTestData() {
        // Create test user and car with unique email
        testUser = createAndSaveUser("evlog-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();

        testCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = testCar.getId();
    }

    @Test
    void shouldCreateChargingLog_WithGeohashing() {
        // Given: Charging log request with GPS coordinates
        double latitude = 52.5200;
        double longitude = 13.4050;
        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                latitude,
                longitude,
                null, // odometerKm
                null, // maxChargingPowerKw
                LocalDateTime.now(),
                null  // ocrUsed
        );

        HttpEntity<EvLogRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/logs
        ResponseEntity<EvLogCreateResponse> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                requestWithAuth,
                EvLogCreateResponse.class
        );

        // Then: Log created successfully with coins awarded
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(carId, response.getBody().log().carId());
        assertEquals(new BigDecimal("50.0"), response.getBody().log().kwhCharged());
        assertEquals(25, response.getBody().coinsAwarded(), "First log must award 25 coins");

        // PRIVACY CHECK: Verify geohash is stored in database (not lat/lon)
        EvLog savedLog = evLogRepository.findById(response.getBody().log().id()).orElseThrow();
        assertNotNull(savedLog.getGeohash());
        assertEquals(5, savedLog.getGeohash().length(), "Geohash must be 5 characters");
    }

    @Test
    void shouldRejectLogCreation_WithoutAuthentication() {
        // Given: Request without JWT token
        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null, null, // No GPS
                null, null, // No odometer, no max power
                LocalDateTime.now(),
                null  // ocrUsed
        );

        // When: POST /api/logs without auth
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/logs",
                request,
                String.class
        );

        // Then: Access denied
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void shouldRejectLogCreation_ForCarNotOwnedByUser() {
        // Given: Another user's car
        User otherUser = createAndSaveUser("other-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.I4);

        EvLogRequest request = new EvLogRequest(
                otherUserCar.getId(),
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null, null, // No GPS
                null, null, // No odometer, no max power
                LocalDateTime.now(),
                null  // ocrUsed
        );

        HttpEntity<EvLogRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: Try to log for another user's car
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                requestWithAuth,
                String.class
        );

        // Then: Should be rejected (500 with IllegalArgumentException)
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldGetLogsForUser() {
        // Given: User has 2 charging logs
        EvLog log1 = TestDataBuilder.createTestEvLog(carId, new BigDecimal("50.0"), new BigDecimal("12.50"));
        EvLog log2 = TestDataBuilder.createTestEvLog(carId, new BigDecimal("30.0"), new BigDecimal("9.00"));
        evLogRepository.save(log1);
        evLogRepository.save(log2);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/logs
        ResponseEntity<List<EvLogResponse>> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<EvLogResponse>>() {}
        );

        // Then: Returns all logs for user
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldGetLogsForSpecificCar() {
        // Given: User has 2 cars with logs
        Car anotherCar = createAndSaveCar(userId, CarBrand.CarModel.I4);

        EvLog log1 = TestDataBuilder.createTestEvLog(carId, new BigDecimal("50.0"), new BigDecimal("12.50"));
        EvLog log2 = TestDataBuilder.createTestEvLog(anotherCar.getId(), new BigDecimal("30.0"), new BigDecimal("9.00"));
        evLogRepository.save(log1);
        evLogRepository.save(log2);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/logs?carId=xxx (filter by first car)
        ResponseEntity<List<EvLogResponse>> response = restTemplate.exchange(
                "/api/logs?carId=" + carId,
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<EvLogResponse>>() {}
        );

        // Then: Returns only logs for specific car
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(carId, response.getBody().get(0).carId());
    }

    @Test
    void shouldNotSeeOtherUsersLogs_SecurityCheck() {
        // Given: Another user with their own logs
        User otherUser = createAndSaveUser("other-security-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.I4);
        EvLog otherUserLog = TestDataBuilder.createTestEvLog(otherUserCar.getId(),
                new BigDecimal("100.0"), new BigDecimal("25.00"));
        evLogRepository.save(otherUserLog);

        // And: Current user has no logs
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/logs
        ResponseEntity<List<EvLogResponse>> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<EvLogResponse>>() {}
        );

        // Then: User should NOT see other user's logs
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), "User should not see other users' logs");
    }

    @Test
    void shouldGetStatisticsForCar() {
        // Given: Car has multiple charging logs
        LocalDateTime now = LocalDateTime.now();
        EvLog log1 = TestDataBuilder.createTestEvLogWithTimestamp(carId,
                new BigDecimal("50.0"), new BigDecimal("12.50"), now.minusDays(3));
        EvLog log2 = TestDataBuilder.createTestEvLogWithTimestamp(carId,
                new BigDecimal("30.0"), new BigDecimal("9.00"), now.minusDays(2));
        EvLog log3 = TestDataBuilder.createTestEvLogWithTimestamp(carId,
                new BigDecimal("20.0"), new BigDecimal("5.00"), now.minusDays(1));
        evLogRepository.save(log1);
        evLogRepository.save(log2);
        evLogRepository.save(log3);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/logs/statistics?carId=xxx
        ResponseEntity<EvLogStatisticsResponse> response = restTemplate.exchange(
                "/api/logs/statistics?carId=" + carId,
                HttpMethod.GET,
                requestWithAuth,
                EvLogStatisticsResponse.class
        );

        // Then: Returns statistics
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().totalCharges());
        // Use compareTo for BigDecimal to ignore scale differences (100.0 vs 100.00)
        assertEquals(0, new BigDecimal("100.0").compareTo(response.getBody().totalKwhCharged()));
        assertEquals(0, new BigDecimal("26.50").compareTo(response.getBody().totalCostEur()));
        assertFalse(response.getBody().chargesOverTime().isEmpty());
    }

    @Test
    void shouldRejectStatistics_ForCarNotOwnedByUser() {
        // Given: Another user's car
        User otherUser = createAndSaveUser("other-stats-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.I4);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: Try to get statistics for other user's car
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/logs/statistics?carId=" + otherUserCar.getId(),
                HttpMethod.GET,
                requestWithAuth,
                String.class
        );

        // Then: Should be rejected
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnEmptyStatistics_WhenNoLogs() {
        // Given: Car exists but has no logs
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/logs/statistics?carId=xxx
        ResponseEntity<EvLogStatisticsResponse> response = restTemplate.exchange(
                "/api/logs/statistics?carId=" + carId,
                HttpMethod.GET,
                requestWithAuth,
                EvLogStatisticsResponse.class
        );

        // Then: Returns empty statistics (not error)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalCharges());
        assertEquals(BigDecimal.ZERO, response.getBody().totalKwhCharged());
    }
}
