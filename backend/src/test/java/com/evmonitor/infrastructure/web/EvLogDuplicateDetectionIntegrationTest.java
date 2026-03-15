package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.InternalEvLogRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.CoinLog;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for bidirectional duplicate detection (superseded_by).
 *
 * Ensures that when a USER_LOGGED entry and an import entry cover the same charging session,
 * the import is hidden (superseded_by != null) and USER_LOGGED wins. On deletion of the
 * user log, the import resurfaces automatically (ON DELETE SET NULL FK).
 */
class EvLogDuplicateDetectionIntegrationTest extends AbstractIntegrationTest {

    private static final String INTERNAL_TOKEN = "dev-internal-token-change-in-prod";

    private User testUser;
    private Car testCar;
    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("dupdetect-" + System.nanoTime() + "@ev-monitor.net");
        userId = testUser.getId();
        testCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        carId = testCar.getId();
    }

    /**
     * Test 1: USER_LOGGED created after a Tesla import already exists.
     * Posting a manual log should suppress the matching import.
     */
    @Test
    void userLogAfterImport_suppressesImport() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(1);
        BigDecimal kwh = new BigDecimal("45.00");

        // Given: A Tesla import already exists for this session
        EvLog importLog = evLogRepository.save(EvLog.createFromInternal(
                carId,
                kwh,
                60,
                null,
                sessionTime,
                null, null,
                DataSource.TESLA_FLEET_IMPORT,
                null,
                ChargingType.DC));

        assertNull(importLog.getSupersededBy(), "Import should not be suppressed yet");

        // When: User manually enters a log for the same session (within ±15 min, ±15% kWh)
        EvLogRequest request = new EvLogRequest(
                carId,
                kwh,
                new BigDecimal("12.00"),
                65,
                null, null,
                50000, null, 85,
                sessionTime.plusMinutes(3),
                null, null, null, null
        );

        ResponseEntity<EvLogCreateResponse> response = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(request, userId, testUser.getEmail()),
                EvLogCreateResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        UUID userLogId = response.getBody().log().id();

        // Then: The import is superseded by the user log
        EvLog importAfter = evLogRepository.findById(importLog.getId()).orElseThrow();
        assertEquals(userLogId, importAfter.getSupersededBy(),
                "Import log should be superseded by the user log");

        // And: The import does NOT appear in the visible log list
        var logs = evLogRepository.findAllByCarId(carId);
        assertTrue(logs.stream().anyMatch(l -> l.getId().equals(userLogId)),
                "User log should be visible");
        assertTrue(logs.stream().noneMatch(l -> l.getId().equals(importLog.getId())),
                "Superseded import should be hidden from list");
    }

    /**
     * Test 2: Tesla import arrives after USER_LOGGED already exists.
     * The import should be immediately marked as superseded.
     */
    @Test
    void importAfterUserLog_isImmediatelySuperseded() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(2);
        BigDecimal kwh = new BigDecimal("38.50");

        // Given: User has already manually logged this session
        EvLog userLog = evLogRepository.save(EvLog.createNew(
                carId, kwh, new BigDecimal("11.00"),
                55, null, 51000, null, 82,
                sessionTime,
                ChargingType.AC, null, null));

        // When: Tesla import comes in for the same session (5 min later, same kWh)
        InternalEvLogRequest importRequest = new InternalEvLogRequest(
                carId,
                userId,
                kwh,
                55,
                sessionTime.plusMinutes(5),
                null,
                null, null,
                DataSource.TESLA_FLEET_IMPORT.name(),
                null,
                ChargingType.DC.name()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", INTERNAL_TOKEN);
        headers.set("Content-Type", "application/json");

        ResponseEntity<String> importResponse = restTemplate.exchange(
                "/api/internal/logs",
                HttpMethod.POST,
                new HttpEntity<>(importRequest, headers),
                String.class
        );

        assertEquals(HttpStatus.OK, importResponse.getStatusCode());

        // Then: The import should be superseded by the existing user log
        // Find the import log by its dataSource (newest, non-USER_LOGGED entry for this car)
        Optional<EvLog> importedLog = evLogRepository.findAll().stream()
                .filter(l -> l.getCarId().equals(carId)
                        && l.getDataSource() == DataSource.TESLA_FLEET_IMPORT)
                .findFirst();

        assertTrue(importedLog.isPresent(), "Import log should exist in DB");
        assertEquals(userLog.getId(), importedLog.get().getSupersededBy(),
                "Import should be superseded by the pre-existing user log");

        // And: Import does NOT appear in the visible log list
        var visibleLogs = evLogRepository.findAllByCarId(carId);
        assertTrue(visibleLogs.stream().anyMatch(l -> l.getId().equals(userLog.getId())),
                "User log should be visible");
        assertTrue(visibleLogs.stream().noneMatch(l -> l.getId().equals(importedLog.get().getId())),
                "Superseded import should not appear in list");
    }

    /**
     * Test 3: Reversal — when the user log is deleted, the import resurfaces.
     * FK ON DELETE SET NULL handles this automatically.
     */
    @Test
    void deletingUserLog_revealsSuppressedImport() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(3);
        BigDecimal kwh = new BigDecimal("52.00");

        // Given: Import exists and is superseded by a user log
        EvLog importLog = evLogRepository.save(EvLog.createFromInternal(
                carId,
                kwh,
                70,
                null,
                sessionTime,
                null, null,
                DataSource.TESLA_LIVE,
                null,
                ChargingType.AC));

        // Create user log via API (this triggers suppressDuplicateImports)
        EvLogRequest request = new EvLogRequest(
                carId,
                kwh,
                new BigDecimal("14.50"),
                72,
                null, null,
                52000, null, 88,
                sessionTime.minusMinutes(2),
                null, null, null, null
        );

        ResponseEntity<EvLogCreateResponse> createResponse = restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(request, userId, testUser.getEmail()),
                EvLogCreateResponse.class
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        UUID userLogId = createResponse.getBody().log().id();

        // Verify import is suppressed
        EvLog importBefore = evLogRepository.findById(importLog.getId()).orElseThrow();
        assertEquals(userLogId, importBefore.getSupersededBy(),
                "Import should be superseded before deletion");

        // When: User deletes the manual log
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/logs/" + userLogId,
                HttpMethod.DELETE,
                createAuthRequest(userId, testUser.getEmail()),
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Then: The import's superseded_by is NULL again (ON DELETE SET NULL)
        EvLog importAfter = evLogRepository.findById(importLog.getId()).orElseThrow();
        assertNull(importAfter.getSupersededBy(),
                "Import should resurface after user log deletion (superseded_by = NULL)");

        // And: The import now appears in the visible log list
        var visibleLogs = evLogRepository.findAllByCarId(carId);
        assertTrue(visibleLogs.stream().anyMatch(l -> l.getId().equals(importLog.getId())),
                "Resurfaced import should be visible in list");
    }

    /**
     * Test 5: Coins — Import first, User-Log second.
     * Import gets TESLA_DAILY_LOG coins (+5), then when the user logs manually,
     * those coins are reverted (-5) and manual log coins are awarded separately.
     */
    @Test
    void userLogAfterImport_revertsImportCoins() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(5);
        BigDecimal kwh = new BigDecimal("40.00");

        // Given: Tesla import already exists and was awarded coins via internal endpoint
        InternalEvLogRequest importRequest = new InternalEvLogRequest(
                carId, userId, kwh, 60, sessionTime,
                null, null, null,
                DataSource.TESLA_FLEET_IMPORT.name(),
                null, ChargingType.DC.name()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", INTERNAL_TOKEN);
        headers.set("Content-Type", "application/json");
        restTemplate.exchange("/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(importRequest, headers), String.class);

        // Verify import coins were awarded (+5 TESLA_DAILY_LOG)
        EvLog importLog = evLogRepository.findAll().stream()
                .filter(l -> l.getCarId().equals(carId) && l.getDataSource() == DataSource.TESLA_FLEET_IMPORT)
                .findFirst().orElseThrow();
        int importCoins = coinLogRepository.sumCoinsForSourceEntity(importLog.getId());
        assertEquals(5, importCoins, "Import should have received TESLA_DAILY_LOG coins (+5)");

        // When: User manually logs the same session
        EvLogRequest userRequest = new EvLogRequest(
                carId, kwh, new BigDecimal("11.00"), 62,
                null, null, 51000, null, 83,
                sessionTime.plusMinutes(4),
                null, null, null, null
        );
        ResponseEntity<EvLogCreateResponse> response = restTemplate.exchange(
                "/api/logs", HttpMethod.POST,
                createAuthRequest(userRequest, userId, testUser.getEmail()),
                EvLogCreateResponse.class
        );
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Then: Import coins are reverted (negative entry with import log as sourceEntityId)
        List<CoinLog> allCoins = coinLogRepository.findAllByUserId(userId);

        boolean hasRevert = allCoins.stream().anyMatch(c ->
                c.getAmount() < 0
                && importLog.getId().equals(c.getSourceEntityId())
                && c.getActionDescription().equals("Import als Duplikat erkannt"));
        assertTrue(hasRevert, "A negative coin entry for the import should exist");

        // And: Net coins for the import log = 0 (awarded then reverted)
        int netImportCoins = coinLogRepository.sumCoinsForSourceEntity(importLog.getId());
        assertEquals(0, netImportCoins, "Net coins for superseded import should be 0");
    }

    /**
     * Test 6: Coins — User-Log first, Import second.
     * Import arrives after user already logged → import is immediately superseded → gets NO coins.
     */
    @Test
    void importAfterUserLog_getsNoCoins() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(6);
        BigDecimal kwh = new BigDecimal("33.00");

        // Given: User has already manually logged this session
        evLogRepository.save(EvLog.createNew(
                carId, kwh, new BigDecimal("9.00"),
                50, null, 52000, null, 78,
                sessionTime, ChargingType.AC, null, null));

        // When: Tesla import arrives for the same session
        InternalEvLogRequest importRequest = new InternalEvLogRequest(
                carId, userId, kwh, 50, sessionTime.plusMinutes(6),
                null, null, null,
                DataSource.TESLA_FLEET_IMPORT.name(),
                null, ChargingType.DC.name()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", INTERNAL_TOKEN);
        headers.set("Content-Type", "application/json");
        restTemplate.exchange("/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(importRequest, headers), String.class);

        // Then: Import is superseded and got NO coins
        EvLog importLog = evLogRepository.findAll().stream()
                .filter(l -> l.getCarId().equals(carId) && l.getDataSource() == DataSource.TESLA_FLEET_IMPORT)
                .findFirst().orElseThrow();

        assertNotNull(importLog.getSupersededBy(), "Import should be superseded");
        int importCoins = coinLogRepository.sumCoinsForSourceEntity(importLog.getId());
        assertEquals(0, importCoins, "Superseded import should have received no coins");
    }

    /**
     * Test 4: Non-matching import (different kWh) should NOT be suppressed.
     */
    @Test
    void userLog_doesNotSuppressImportWithDifferentKwh() {
        LocalDateTime sessionTime = LocalDateTime.now().minusHours(4);

        // Given: An import with 45 kWh
        EvLog importLog = evLogRepository.save(EvLog.createFromInternal(
                carId,
                new BigDecimal("45.00"),
                60,
                null,
                sessionTime,
                null, null,
                DataSource.TESLA_FLEET_IMPORT,
                null,
                ChargingType.DC));

        // When: User logs a very different amount (28 kWh — >15% difference)
        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("28.00"),
                new BigDecimal("7.00"),
                40,
                null, null,
                50000, null, 60,
                sessionTime.plusMinutes(5),
                null, null, null, null
        );

        restTemplate.exchange(
                "/api/logs",
                HttpMethod.POST,
                createAuthRequest(request, userId, testUser.getEmail()),
                EvLogCreateResponse.class
        );

        // Then: Import is NOT suppressed (kWh difference too large)
        EvLog importAfter = evLogRepository.findById(importLog.getId()).orElseThrow();
        assertNull(importAfter.getSupersededBy(),
                "Import should NOT be suppressed when kWh difference exceeds 15%");
    }
}
