package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.spritmonitor.SpritMonitorFuelingDTO;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration Tests for SpritMonitorImportController.
 * Tests Sprit-Monitor import flow with mocked external API.
 *
 * PRIVACY CRITICAL: GPS coordinates from Sprit-Monitor must be converted to geohash!
 * SECURITY CRITICAL: User must only import to their own cars!
 */
class SpritMonitorImportIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private SpritMonitorClient spritMonitorClient;

    private User testUser;
    private Car testCar;
    private UUID userId;
    private UUID carId;
    private String validToken;

    @BeforeEach
    void setUpTestData() {
        // Create test user and car with unique email
        testUser = createAndSaveUser("sprit-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();

        testCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = testCar.getId();

        validToken = "valid-sprit-monitor-token";
    }

    @Test
    void shouldFetchVehicles_FromSpritMonitor() {
        // Given: Sprit-Monitor returns electric vehicles
        List<SpritMonitorVehicleDTO> mockVehicles = List.of(
                new SpritMonitorVehicleDTO(123, "Tesla", "Model 3", 5),
                new SpritMonitorVehicleDTO(456, "BMW", "i4", 5)
        );

        when(spritMonitorClient.getVehicles(validToken)).thenReturn(mockVehicles);

        // When: POST /api/import/sprit-monitor/vehicles
        Map<String, String> request = Map.of("token", validToken);
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles",
                HttpMethod.POST,
                requestWithAuth,
                List.class
        );

        // Then: Returns vehicles
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldRejectVehicleFetch_WithoutToken() {
        // Given: Request without token
        Map<String, String> request = Map.of();
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/import/sprit-monitor/vehicles
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles",
                HttpMethod.POST,
                requestWithAuth,
                Map.class
        );

        // Then: Bad request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shouldImportFuelings_WithGeohashConversion() {
        // Given: Sprit-Monitor returns fuelings with GPS coordinates
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"), // Berlin Mitte latitude
                new BigDecimal("13.4050")  // Berlin Mitte longitude
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("50.0"),
                        new BigDecimal("12.50"),
                        60,
                        position,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123))).thenReturn(mockFuelings);

        // When: POST /api/import/sprit-monitor/fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings",
                HttpMethod.POST,
                requestWithAuth,
                ImportResult.class
        );

        // Then: Import successful
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getImported());

        // PRIVACY CHECK: Verify GPS coordinates were converted to geohash
        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());

        EvLog importedLog = importedLogs.get(0);
        assertNotNull(importedLog.getGeohash(), "Geohash must be set");
        assertEquals(5, importedLog.getGeohash().length(), "Geohash must be 5 characters");
        // Use compareTo for BigDecimal to ignore scale differences
        assertEquals(0, new BigDecimal("50.0").compareTo(importedLog.getKwhCharged()));
        assertEquals(0, new BigDecimal("12.50").compareTo(importedLog.getCostEur()));
    }

    @Test
    void shouldHandleImportWithoutGPSCoordinates() {
        // Given: Fueling without GPS coordinates (null position)
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("50.0"),
                        new BigDecimal("12.50"),
                        60,
                        null, // No GPS position
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123))).thenReturn(mockFuelings);

        // When: Import fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings",
                HttpMethod.POST,
                requestWithAuth,
                ImportResult.class
        );

        // Then: Import successful, geohash is null
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());
        assertNull(importedLogs.get(0).getGeohash(), "Geohash should be null when no GPS provided");
    }

    @Test
    void shouldHandlePartialImportFailure() {
        // Given: Some fuelings are valid, some are invalid
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"),
                new BigDecimal("13.4050")
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("50.0"),
                        new BigDecimal("12.50"),
                        60,
                        position,
                        null,
                        null
                ),
                new SpritMonitorFuelingDTO(
                        "invalid-date-format", // Invalid date!
                        new BigDecimal("30.0"),
                        new BigDecimal("9.00"),
                        45,
                        position,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123))).thenReturn(mockFuelings);

        // When: Import fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings",
                HttpMethod.POST,
                requestWithAuth,
                ImportResult.class
        );

        // Then: Partial import (1 success, 1 error)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ImportResult result = response.getBody();
        assertNotNull(result);
        assertEquals(1, result.getImported(), "One valid fueling should be imported");
        assertFalse(result.getErrors().isEmpty(), "Should have error for invalid fueling");
    }

    @Test
    void shouldRejectImport_WithoutAuthentication() {
        // Given: Request without JWT token
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "carId", carId.toString()
        );

        // When: POST without auth
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/import/sprit-monitor/fuelings",
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
    void shouldHandleSpritMonitorAPIError() {
        // Given: Sprit-Monitor API returns error
        when(spritMonitorClient.getVehicles(validToken))
                .thenThrow(new RuntimeException("Sprit-Monitor API error"));

        // When: Fetch vehicles
        Map<String, String> request = Map.of("token", validToken);
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles",
                HttpMethod.POST,
                requestWithAuth,
                Map.class
        );

        // Then: Error response
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shouldImportMultipleFuelings() {
        // Given: Multiple fuelings
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"),
                new BigDecimal("13.4050")
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), new BigDecimal("12.50"), 60, position, null, null),
                new SpritMonitorFuelingDTO("20.01.2024", new BigDecimal("30.0"), new BigDecimal("9.00"), 45, position, null, null),
                new SpritMonitorFuelingDTO("25.01.2024", new BigDecimal("40.0"), new BigDecimal("10.00"), 50, position, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123))).thenReturn(mockFuelings);

        // When: Import fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings",
                HttpMethod.POST,
                requestWithAuth,
                ImportResult.class
        );

        // Then: All fuelings imported
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().getImported());

        // Verify all logs in database
        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(3, importedLogs.size());
    }
}
