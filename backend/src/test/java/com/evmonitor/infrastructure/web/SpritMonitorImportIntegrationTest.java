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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration Tests for SpritMonitorImportController.
 * Tests Sprit-Monitor import flow with mocked external API.
 *
 * PRIVACY CRITICAL: GPS coordinates from Sprit-Monitor must be converted to geohash!
 * SECURITY CRITICAL: User must only import to their own cars!
 */
class SpritMonitorImportIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private SpritMonitorClient spritMonitorClient;

    private User testUser;
    private Car testCar;
    private UUID userId;
    private UUID carId;
    private String validToken;

    // mainTank=1 is the default electric tank ID used in tests
    private static final int MAIN_TANK_ID = 1;
    private static final int QUANTITY_UNIT_KWH = 5;

    @BeforeEach
    void setUpTestData() {
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
                new SpritMonitorVehicleDTO(123, "Tesla", "Model 3", 5, 1),
                new SpritMonitorVehicleDTO(456, "BMW", "i4", 5, 1)
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
                        QUANTITY_UNIT_KWH,
                        null,
                        new BigDecimal("12.50"),
                        60,
                        null, // percent (SoC after charging)
                        null, // chargingPower
                        position,
                        null,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        // When: POST /api/import/sprit-monitor/fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
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
        assertEquals(6, importedLog.getGeohash().length(), "Geohash must be 6 characters");
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
                        QUANTITY_UNIT_KWH,
                        null,
                        new BigDecimal("12.50"),
                        60,
                        null, // percent (SoC after charging)
                        null, // chargingPower
                        null, // No GPS position
                        null,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        // When: Import fuelings
        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
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
    void shouldSkipFuelings_WithNonKwhUnit() {
        // Given: Fuelings with non-kWh unit (e.g. liters) must be skipped
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), 1 /* Liter */, null, new BigDecimal("12.50"), 60, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("20.01.2024", new BigDecimal("30.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("9.00"), 45, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of("token", validToken, "vehicleId", 123, "mainTankId", MAIN_TANK_ID, "carId", carId.toString());
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: Only kWh entry imported, liter entry skipped
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());
        assertEquals(1, response.getBody().getSkipped());
    }

    @Test
    void shouldHandlePartialImportFailure() {
        // Given: One valid fueling, one with invalid date
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"),
                new BigDecimal("13.4050")
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("12.50"), 60, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("invalid-date-format", new BigDecimal("30.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("9.00"), 45, null, null, position, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

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
                "mainTankId", MAIN_TANK_ID,
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shouldImportOdometerValue() {
        // Given: Fueling with odometer value — this was the bug: odometer was always null before the fix
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("55.0"),
                        QUANTITY_UNIT_KWH,
                        new BigDecimal("42350.7"), // 42350 km on the odometer
                        new BigDecimal("14.00"),
                        75,
                        null, // percent (SoC after charging)
                        null, // chargingPower
                        null,
                        null,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        // THE CRITICAL ASSERTION: odometer must not be null after the fix
        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());
        assertEquals(42350, importedLogs.get(0).getOdometerKm(),
                "Odometer must be mapped from SpritMonitor (was always null before the fix)");
    }

    @Test
    void shouldForwardCorrectTankIdToClient() {
        // Given: User has EV tank with ID=3 (not the default tank 1)
        int electricTankId = 3;
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), eq(electricTankId))).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", electricTankId,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported(), "Fueling must be imported via tank ID 3");

        // Verify client was called with the correct non-default tank ID
        verify(spritMonitorClient).getFuelings(eq(validToken), eq(123), eq(electricTankId));
    }

    @Test
    void shouldSkipDuplicateFuelings_OnReimport() {
        // Given: One fueling already imported
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // First import
        restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // When: Re-import same data
        ResponseEntity<ImportResult> secondResponse = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: Second import skips the already-imported entry
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        assertEquals(0, secondResponse.getBody().getImported(), "Re-import must not create duplicates");
        assertEquals(1, secondResponse.getBody().getSkipped(), "Already-imported entry must be counted as skipped");

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, logs.size(), "Only one log should exist after re-import");
    }

    @Test
    void shouldImportSocAndChargingPower() {
        // Given: Fueling with SoC (percent) and charging power — both were missing before this fix
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("40.0"),
                        QUANTITY_UNIT_KWH,
                        new BigDecimal("25000"),
                        new BigDecimal("10.00"),
                        60,
                        new BigDecimal("80.5"), // SoC after charging: 80%
                        new BigDecimal("11.0"),  // charging power: 11 kW
                        null,
                        null,
                        null,
                        null
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());
        EvLog log = importedLogs.get(0);
        assertEquals(80, log.getSocAfterChargePercent(), "SoC must be mapped from SpritMonitor 'percent' field");
        assertEquals(0, new BigDecimal("11.0").compareTo(log.getMaxChargingPowerKw()), "Charging power must be mapped");
    }

    @Test
    void shouldImportMultipleChargings_OnSameDay_InOdometerAscOrder() {
        // Given: Two charges on the same day — SpritMonitor returns them newest-first (odometer DESC)
        // The fix must sort them ASC so that lower odometer (= earlier charge) gets 00:00:00
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                // SpritMonitor order: higher odometer first (newest-first)
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13978"), new BigDecimal("10.50"), 30, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("79.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13703"), new BigDecimal("30.71"), 60, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: Both charges on the same day must be imported
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported(), "Both charges on the same day must be imported");
        assertEquals(0, response.getBody().getSkipped());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, importedLogs.size(), "Both same-day charges must be persisted");

        // THE CRITICAL ASSERTION: lower odometer (earlier charge) must get 00:00:00
        List<EvLog> sorted = importedLogs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();
        assertEquals(13703, sorted.get(0).getOdometerKm(),
                "Earlier charge (lower odometer) must be at 00:00:00");
        assertEquals(13978, sorted.get(1).getOdometerKm(),
                "Later charge (higher odometer) must be at 00:00:01");
    }

    @Test
    void shouldSkipSameDayDuplicates_OnReimport() {
        // Given: Two charges on the same day, imported once
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("8.00"), 30, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("45.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("13.00"), 60, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // First import
        restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // When: Re-import same data
        ResponseEntity<ImportResult> secondResponse = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: No duplicates created
        assertEquals(0, secondResponse.getBody().getImported(), "Re-import must not create duplicates");
        assertEquals(2, secondResponse.getBody().getSkipped(), "Both same-day charges must be detected as already imported");

        assertEquals(2, evLogRepository.findAllByCarId(carId).size(), "Still exactly 2 logs after re-import");
    }

    @Test
    void shouldImportMultipleFuelings() {
        // Given: Multiple fuelings in kWh
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"),
                new BigDecimal("13.4050")
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("12.50"), 60, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("20.01.2024", new BigDecimal("30.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("9.00"), 45, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("25.01.2024", new BigDecimal("40.0"), QUANTITY_UNIT_KWH, null, new BigDecimal("10.00"), 50, null, null, position, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: All fuelings imported
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().getImported());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(3, importedLogs.size());
    }

    @Test
    void shouldDeserializePosition_FromStringFormat() throws Exception {
        // SpritMonitor sends position as "lat,lon" string — NOT as a JSON object.
        // This caused "Failed to fetch fuelings: Error while extracting response" in production
        // when a fueling entry had a position value (not all entries do).
        String json = """
                [{
                    "date": "15.01.2024",
                    "quantity": 50.0,
                    "quantityunitid": 5,
                    "cost": 12.50,
                    "position": "51.194004,6.813039"
                }]
                """;

        ObjectMapper mapper = new ObjectMapper();
        List<SpritMonitorFuelingDTO> fuelings = mapper.readValue(json, new TypeReference<>() {});

        assertEquals(1, fuelings.size());
        SpritMonitorFuelingDTO fueling = fuelings.get(0);
        assertNotNull(fueling.position(), "Position must be parsed from string");
        assertEquals(0, new BigDecimal("51.194004").compareTo(fueling.position().lat()),
                "Latitude must be parsed correctly");
        assertEquals(0, new BigDecimal("6.813039").compareTo(fueling.position().lon()),
                "Longitude must be parsed correctly");
    }

    @Test
    void shouldImportFuelings_WithSameOdometer_AsStandaloneLogsWithoutGrouping() {
        // Grouping von SpritMonitor-Logs mit gleichem Kilometerstand wird im Frontend behandelt.
        // Das Backend importiert alle Logs als eigenständige Einträge ohne session_group_id.
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("28.10.2025", new BigDecimal("11.02"), QUANTITY_UNIT_KWH,
                        new BigDecimal("113487"), new BigDecimal("4.00"), 20, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("28.10.2025", new BigDecimal("40.00"), QUANTITY_UNIT_KWH,
                        new BigDecimal("113487"), new BigDecimal("15.00"), 60, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of("token", validToken, "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID, "carId", carId.toString());
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getSessionGroupId() == null),
                "Logs mit gleichem Odometer dürfen nicht mehr serverseitig gruppiert werden");
        assertTrue(logs.stream().allMatch(EvLog::isIncludeInStatistics),
                "Alle SpritMonitor-Logs müssen in Statistiken enthalten sein");
    }

    @Test
    void shouldNotGroup_FuelingsWithDifferentOdometers() {
        // Zwei Ladevorgänge an verschiedenen Stopps (unterschiedlicher Kilometerstand) — dürfen NICHT gruppiert werden.
        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("79.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13703"), new BigDecimal("30.71"), 60, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13978"), new BigDecimal("10.50"), 30, null, null, null, null, null, null)
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of("token", validToken, "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID, "carId", carId.toString());
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());

        // Beide Logs müssen standalone bleiben
        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getSessionGroupId() == null),
                "Logs mit verschiedenen Odometer-Werten dürfen nicht gruppiert werden");
        assertTrue(logs.stream().allMatch(EvLog::isIncludeInStatistics),
                "Standalone-Logs müssen in Statistiken enthalten sein");
    }

    @Test
    void shouldHandleNullPosition_InStringFormat() throws Exception {
        // Fuelings without GPS have no position field at all — must not throw
        String json = """
                [{
                    "date": "15.01.2024",
                    "quantity": 50.0,
                    "quantityunitid": 5,
                    "cost": 12.50
                }]
                """;

        ObjectMapper mapper = new ObjectMapper();
        List<SpritMonitorFuelingDTO> fuelings = mapper.readValue(json, new TypeReference<>() {});

        assertEquals(1, fuelings.size());
        assertNull(fuelings.get(0).position(), "Position must be null when not present in JSON");
    }

    @Test
    void shouldPersistRawImportDataAsJsonb() {
        // Given: Fueling with all fields populated (complex JSON structure)
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"),
                new BigDecimal("13.4050")
        );

        List<SpritMonitorFuelingDTO> mockFuelings = List.of(
                new SpritMonitorFuelingDTO(
                        "15.01.2024",
                        new BigDecimal("55.5"),
                        QUANTITY_UNIT_KWH,
                        new BigDecimal("42350.7"),
                        new BigDecimal("14.50"),
                        75,
                        new BigDecimal("85.0"), // percent (SoC)
                        new BigDecimal("11.0"), // charging power
                        position,
                        "Supercharger Berlin", // station name
                        "Test note from SpritMonitor",
                        "AC, Type 2, 11 kW" // charge_info
                )
        );

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(mockFuelings);

        Map<String, Object> request = Map.of(
                "token", validToken,
                "vehicleId", 123,
                "mainTankId", MAIN_TANK_ID,
                "carId", carId.toString()
        );
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: Import fueling
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        // Then: Import successful
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        // Verify raw_import_data was persisted as JSONB
        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());

        EvLog log = importedLogs.get(0);
        assertNotNull(log.getRawImportData(), "raw_import_data should be persisted");

        // Verify it's valid JSON containing the original DTO data
        String rawJson = log.getRawImportData();
        assertTrue(rawJson.contains("\"date\":\"15.01.2024\""), "Should contain original date");
        assertTrue(rawJson.contains("\"quantity\":55.5"), "Should contain original quantity");
        assertTrue(rawJson.contains("\"odometer\":42350.7"), "Should contain original odometer");
        assertTrue(rawJson.contains("\"lat\":52.52"), "Should contain position data");
        assertTrue(rawJson.contains("\"charge_info\":\"AC"), "Should contain charge info with AC");
    }
}
