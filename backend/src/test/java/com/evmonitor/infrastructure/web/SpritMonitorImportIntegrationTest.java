package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.application.spritmonitor.RawFueling;
import com.evmonitor.application.spritmonitor.RefreshRawResult;
import com.evmonitor.application.spritmonitor.SpritMonitorFuelingDTO;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
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
import java.time.LocalDateTime;
import java.util.Arrays;
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RawFueling wrap(SpritMonitorFuelingDTO dto) {
        try {
            return new RawFueling(dto, new ObjectMapper().writeValueAsString(dto));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<RawFueling> raw(SpritMonitorFuelingDTO... dtos) {
        return Arrays.stream(dtos).map(this::wrap).toList();
    }

    private List<RawFueling> raw(List<SpritMonitorFuelingDTO> dtos) {
        return dtos.stream().map(this::wrap).toList();
    }

    private Map<String, Object> importRequest(int vehicleId) {
        return Map.of("token", validToken, "vehicleId", vehicleId,
                "mainTankId", MAIN_TANK_ID, "carId", carId.toString());
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldFetchVehicles_FromSpritMonitor() {
        List<SpritMonitorVehicleDTO> mockVehicles = List.of(
                new SpritMonitorVehicleDTO(123, "Tesla", "Model 3", 5, 1),
                new SpritMonitorVehicleDTO(456, "BMW", "i4", 5, 1)
        );

        when(spritMonitorClient.getVehicles(validToken)).thenReturn(mockVehicles);

        Map<String, String> request = Map.of("token", validToken);
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles", HttpMethod.POST, requestWithAuth, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldRejectVehicleFetch_WithoutToken() {
        Map<String, String> request = Map.of();
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles", HttpMethod.POST, requestWithAuth, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shouldImportFuelings_WithGeohashConversion() {
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"), new BigDecimal("13.4050"));

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, position, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

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
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, importedLogs.size());
        assertNull(importedLogs.get(0).getGeohash(), "Geohash should be null when no GPS provided");
    }

    @Test
    void shouldSkipFuelings_WithNonKwhUnit() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), 1,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("20.01.2024", new BigDecimal("30.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("9.00"), 45, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());
        assertEquals(1, response.getBody().getSkipped());
    }

    @Test
    void shouldHandlePartialImportFailure() {
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"), new BigDecimal("13.4050"));

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("invalid-date-format", new BigDecimal("30.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("9.00"), 45, null, null, position, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ImportResult result = response.getBody();
        assertNotNull(result);
        assertEquals(1, result.getImported(), "One valid fueling should be imported");
        assertFalse(result.getErrors().isEmpty(), "Should have error for invalid fueling");
    }

    @Test
    void shouldRejectImport_WithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/import/sprit-monitor/fuelings", importRequest(123), String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void shouldHandleSpritMonitorAPIError() {
        when(spritMonitorClient.getVehicles(validToken))
                .thenThrow(new RuntimeException("Sprit-Monitor API error"));

        Map<String, String> request = Map.of("token", validToken);
        HttpEntity<Map<String, String>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sprit-monitor/vehicles", HttpMethod.POST, requestWithAuth, Map.class);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shouldImportOdometerValue() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("55.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("42350.7"), new BigDecimal("14.00"), 75,
                        null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        List<EvLog> importedLogs = evLogRepository.findAllByCarId(carId);
        assertEquals(42350, importedLogs.get(0).getOdometerKm());
    }

    @Test
    void shouldForwardCorrectTankIdToClient() {
        int electricTankId = 3;
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), eq(electricTankId))).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null)
        ));

        Map<String, Object> request = Map.of("token", validToken, "vehicleId", 123,
                "mainTankId", electricTankId, "carId", carId.toString());
        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());
        verify(spritMonitorClient).getFuelings(eq(validToken), eq(123), eq(electricTankId));
    }

    @Test
    void shouldSkipDuplicateFuelings_OnReimport() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());

        restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);
        ResponseEntity<ImportResult> secondResponse = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(0, secondResponse.getBody().getImported());
        assertEquals(1, secondResponse.getBody().getSkipped());
        assertEquals(1, evLogRepository.findAllByCarId(carId).size());
    }

    @Test
    void shouldImportSocAndChargingPower() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("40.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("25000"), new BigDecimal("10.00"), 60,
                        new BigDecimal("80.5"), new BigDecimal("11.0"), null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        EvLog log = evLogRepository.findAllByCarId(carId).get(0);
        assertEquals(80, log.getSocAfterChargePercent());
        assertEquals(0, new BigDecimal("11.0").compareTo(log.getMaxChargingPowerKw()));
    }

    @Test
    void shouldImportMultipleChargings_OnSameDay_InOdometerAscOrder() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13978"), new BigDecimal("10.50"), 30, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("79.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13703"), new BigDecimal("30.71"), 60, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());

        List<EvLog> sorted = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt)).toList();
        assertEquals(13703, sorted.get(0).getOdometerKm(), "Earlier charge must be at 00:00:00");
        assertEquals(13978, sorted.get(1).getOdometerKm(), "Later charge must be at 00:00:01");
    }

    @Test
    void shouldSkipSameDayDuplicates_OnReimport() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("8.00"), 30, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("45.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("13.00"), 60, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());

        restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);
        ResponseEntity<ImportResult> secondResponse = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(0, secondResponse.getBody().getImported());
        assertEquals(2, secondResponse.getBody().getSkipped());
        assertEquals(2, evLogRepository.findAllByCarId(carId).size());
    }

    @Test
    void shouldImportMultipleFuelings() {
        SpritMonitorFuelingDTO.Position position = new SpritMonitorFuelingDTO.Position(
                new BigDecimal("52.5200"), new BigDecimal("13.4050"));

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("12.50"), 60, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("20.01.2024", new BigDecimal("30.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("9.00"), 45, null, null, position, null, null, null),
                new SpritMonitorFuelingDTO("25.01.2024", new BigDecimal("40.0"), QUANTITY_UNIT_KWH,
                        null, new BigDecimal("10.00"), 50, null, null, position, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().getImported());
        assertEquals(3, evLogRepository.findAllByCarId(carId).size());
    }

    @Test
    void shouldDeserializePosition_FromStringFormat() throws Exception {
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
        assertNotNull(fueling.position());
        assertEquals(0, new BigDecimal("51.194004").compareTo(fueling.position().lat()));
        assertEquals(0, new BigDecimal("6.813039").compareTo(fueling.position().lon()));
    }

    @Test
    void shouldImportFuelings_WithSameOdometer_AsStandaloneLogsWithoutGrouping() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("28.10.2025", new BigDecimal("11.02"), QUANTITY_UNIT_KWH,
                        new BigDecimal("113487"), new BigDecimal("4.00"), 20, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("28.10.2025", new BigDecimal("40.00"), QUANTITY_UNIT_KWH,
                        new BigDecimal("113487"), new BigDecimal("15.00"), 60, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getSessionGroupId() == null));
        assertTrue(logs.stream().allMatch(EvLog::isIncludeInStatistics));
    }

    @Test
    void shouldNotGroup_FuelingsWithDifferentOdometers() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(raw(
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("79.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13703"), new BigDecimal("30.71"), 60, null, null, null, null, null, null),
                new SpritMonitorFuelingDTO("15.11.2025", new BigDecimal("27.0"), QUANTITY_UNIT_KWH,
                        new BigDecimal("13978"), new BigDecimal("10.50"), 30, null, null, null, null, null, null)
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getSessionGroupId() == null));
        assertTrue(logs.stream().allMatch(EvLog::isIncludeInStatistics));
    }

    // -------------------------------------------------------------------------
    // Refresh Raw Import Data Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldRefreshRawImportData_WhenExactlyOneMatch() {
        LocalDateTime loggedAt = LocalDateTime.of(2024, 1, 15, 0, 0, 0);
        EvLog existing = EvLog.createNewWithSource(
                carId, new BigDecimal("50.0"), BigDecimal.ZERO,
                0, null, null, null, null, loggedAt,
                DataSource.SPRITMONITOR_IMPORT, ChargingType.UNKNOWN, "{\"version\":\"stale\"}");
        evLogRepository.save(existing);

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(List.of(
                new RawFueling(
                        new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                                null, null, 0, null, null, null, null, "fresh-note", null),
                        "{\"date\":\"15.01.2024\",\"quantity\":50.0,\"note\":\"fresh-note\"}"
                )
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<RefreshRawResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/refresh-raw", HttpMethod.POST, requestWithAuth, RefreshRawResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().refreshed());
        assertEquals(0, response.getBody().skipped());

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(1, logs.size());
        EvLog updated = logs.get(0);
        assertTrue(updated.getRawImportData().contains("fresh-note"), "raw_import_data must be updated with fresh content");
        assertEquals(0, new BigDecimal("50.0").compareTo(updated.getKwhCharged()), "kwhCharged must not change");
    }

    @Test
    void shouldSkipRefresh_WhenNoMatchFound() {
        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(List.of(
                new RawFueling(
                        new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("99.0"), QUANTITY_UNIT_KWH,
                                null, null, 0, null, null, null, null, null, null),
                        "{\"date\":\"15.01.2024\",\"quantity\":99.0}"
                )
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<RefreshRawResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/refresh-raw", HttpMethod.POST, requestWithAuth, RefreshRawResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().refreshed());
        assertEquals(1, response.getBody().skipped());
    }

    @Test
    void shouldSkipRefresh_WhenMultipleMatchesOnSameDate() {
        BigDecimal kwhCharged = new BigDecimal("50.0");
        LocalDateTime loggedAt1 = LocalDateTime.of(2024, 1, 15, 0, 0, 0);
        LocalDateTime loggedAt2 = LocalDateTime.of(2024, 1, 15, 0, 0, 1);

        evLogRepository.save(EvLog.createNewWithSource(
                carId, kwhCharged, BigDecimal.ZERO, 0, null, null, null, null, loggedAt1,
                DataSource.SPRITMONITOR_IMPORT, ChargingType.UNKNOWN, "{\"seq\":1}"));
        evLogRepository.save(EvLog.createNewWithSource(
                carId, kwhCharged, BigDecimal.ZERO, 0, null, null, null, null, loggedAt2,
                DataSource.SPRITMONITOR_IMPORT, ChargingType.UNKNOWN, "{\"seq\":2}"));

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any())).thenReturn(List.of(
                new RawFueling(
                        new SpritMonitorFuelingDTO("15.01.2024", kwhCharged, QUANTITY_UNIT_KWH,
                                null, null, 0, null, null, null, null, null, null),
                        "{\"date\":\"15.01.2024\",\"quantity\":50.0}"
                )
        ));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<RefreshRawResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/refresh-raw", HttpMethod.POST, requestWithAuth, RefreshRawResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().refreshed());
        assertEquals(1, response.getBody().skipped());

        List<EvLog> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().anyMatch(l -> "{\"seq\":1}".equals(l.getRawImportData())), "seq:1 must be unchanged");
        assertTrue(logs.stream().anyMatch(l -> "{\"seq\":2}".equals(l.getRawImportData())), "seq:2 must be unchanged");
    }

    @Test
    void shouldHandleNullPosition_InStringFormat() throws Exception {
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
        assertNull(fuelings.get(0).position());
    }

    @Test
    void shouldPersistRawImportDataAsJsonb() {
        // raw JSON explicitly set — simulates what the SpritMonitor API would actually send
        String rawJson = """
                {"date":"15.01.2024","quantity":55.5,"quantityunitid":5,"odometer":42350.7,\
"cost":14.50,"charging_duration":75,"percent":85.0,"charging_power":11.0,\
"position":{"lat":52.52,"lon":13.405},"stationname":"Supercharger Berlin",\
"note":"Test note from SpritMonitor","charge_info":"AC, Type 2, 11 kW"}""";

        SpritMonitorFuelingDTO dto = new SpritMonitorFuelingDTO(
                "15.01.2024", new BigDecimal("55.5"), QUANTITY_UNIT_KWH, new BigDecimal("42350.7"),
                new BigDecimal("14.50"), 75, new BigDecimal("85.0"), new BigDecimal("11.0"),
                new SpritMonitorFuelingDTO.Position(new BigDecimal("52.5200"), new BigDecimal("13.4050")),
                "Supercharger Berlin", "Test note from SpritMonitor", "AC, Type 2, 11 kW");

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any()))
                .thenReturn(List.of(new RawFueling(dto, rawJson)));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        ResponseEntity<ImportResult> response = restTemplate.exchange(
                "/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        EvLog log = evLogRepository.findAllByCarId(carId).get(0);
        assertNotNull(log.getRawImportData());
        assertTrue(log.getRawImportData().contains("\"date\":\"15.01.2024\""));
        assertTrue(log.getRawImportData().contains("\"quantity\":55.5"));
        assertTrue(log.getRawImportData().contains("\"odometer\":42350.7"));
        assertTrue(log.getRawImportData().contains("\"lat\":52.52"));
        assertTrue(log.getRawImportData().contains("\"charge_info\":\"AC"));
    }

    @Test
    void shouldPreserveUnknownSpritMonitorFields_InRawImportData() {
        // SpritMonitor may extend their API with new fields at any time.
        // raw_import_data must store the verbatim wire JSON so future fields are never lost.
        String rawJson = """
                {"date":"15.01.2024","quantity":50.0,"quantityunitid":5,"cost":12.50,\
"future_field":"some_value","another_new_field":42}""";

        SpritMonitorFuelingDTO dto = new SpritMonitorFuelingDTO(
                "15.01.2024", new BigDecimal("50.0"), QUANTITY_UNIT_KWH,
                null, new BigDecimal("12.50"), null, null, null, null, null, null, null);

        when(spritMonitorClient.getFuelings(eq(validToken), eq(123), any()))
                .thenReturn(List.of(new RawFueling(dto, rawJson)));

        HttpEntity<Map<String, Object>> requestWithAuth = createAuthRequest(importRequest(123), userId, testUser.getEmail());
        restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST, requestWithAuth, ImportResult.class);

        EvLog log = evLogRepository.findAllByCarId(carId).get(0);
        assertNotNull(log.getRawImportData());
        assertTrue(log.getRawImportData().contains("future_field"),
                "Unknown SpritMonitor fields must be preserved verbatim in raw_import_data");
        assertTrue(log.getRawImportData().contains("another_new_field"),
                "Unknown SpritMonitor fields must be preserved verbatim in raw_import_data");
    }
}
