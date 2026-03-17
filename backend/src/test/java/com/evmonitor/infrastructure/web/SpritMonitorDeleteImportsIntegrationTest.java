package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.ChargingSessionGroupEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical Safety Test: Verifies that DELETE /api/import/sprit-monitor/delete-all
 * only deletes Sprit-Monitor imports for the authenticated user, and does NOT
 * accidentally delete user-logged entries, other data sources, or other users' data.
 */
class SpritMonitorDeleteImportsIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private User otherUser;
    private UUID testUserId;
    private UUID otherUserId;
    private UUID testCarId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = createAndSaveUser("spritmon-delete-" + System.nanoTime() + "@example.com");
        testUserId = testUser.getId();

        // Create other user
        otherUser = createAndSaveUser("spritmon-other-" + System.nanoTime() + "@example.com");
        otherUserId = otherUser.getId();

        // Create cars
        Car testCar = createAndSaveCar(testUserId, CarBrand.CarModel.MODEL_3);
        testCarId = testCar.getId();

        Car otherCar = createAndSaveCar(otherUserId, CarBrand.CarModel.ID_3);
        otherCarId = otherCar.getId();
    }

    @Test
    void deleteAllImports_onlyDeletesSpritMonitorImports_notUserLogged() {
        // Given: User has mixed data sources
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.USER_LOGGED);
        createLog(testCarId, DataSource.USER_LOGGED);

        assertEquals(4, evLogRepository.findAllByUserId(testUserId).size());

        // When: Delete all Sprit-Monitor imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/sprit-monitor/delete-all",
                HttpMethod.DELETE,
                request,
                Void.class
        );

        // Then: Only Sprit-Monitor imports deleted
        assertEquals(200, response.getStatusCode().value());
        List<EvLog> remaining = evLogRepository.findAllByUserId(testUserId);
        assertEquals(2, remaining.size(), "Only USER_LOGGED entries should remain");
        assertTrue(remaining.stream().allMatch(log -> log.getDataSource().equals(DataSource.USER_LOGGED)));
    }

    @Test
    void deleteAllImports_onlyDeletesOwnImports_notOtherUsers() {
        // Given: Both users have Sprit-Monitor imports
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(otherCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(otherCarId, DataSource.SPRITMONITOR_IMPORT);

        assertEquals(2, evLogRepository.findAllByUserId(testUserId).size());
        assertEquals(2, evLogRepository.findAllByUserId(otherUserId).size());

        // When: Test user deletes their imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/sprit-monitor/delete-all",
                HttpMethod.DELETE,
                request,
                Void.class
        );

        // Then: Only test user's imports deleted, other user untouched
        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, evLogRepository.findAllByUserId(testUserId).size());
        assertEquals(2, evLogRepository.findAllByUserId(otherUserId).size(),
                "Other user's data MUST NOT be affected");
    }

    @Test
    void deleteAllImports_doesNotDeleteTeslaImports() {
        // Given: User has multiple import sources
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.TESLA_IMPORT);
        createLog(testCarId, DataSource.TESLA_FLEET_IMPORT);
        createLog(testCarId, DataSource.WALLBOX_OCPP);

        assertEquals(4, evLogRepository.findAllByUserId(testUserId).size());

        // When: Delete Sprit-Monitor imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        restTemplate.exchange("/api/import/sprit-monitor/delete-all", HttpMethod.DELETE, request, Void.class);

        // Then: Only Sprit-Monitor deleted, all other imports preserved
        List<EvLog> remaining = evLogRepository.findAllByUserId(testUserId);
        assertEquals(3, remaining.size());
        assertFalse(remaining.stream().anyMatch(log -> log.getDataSource().equals(DataSource.SPRITMONITOR_IMPORT)));
        assertTrue(remaining.stream().anyMatch(log -> log.getDataSource().equals(DataSource.TESLA_IMPORT)));
        assertTrue(remaining.stream().anyMatch(log -> log.getDataSource().equals(DataSource.TESLA_FLEET_IMPORT)));
        assertTrue(remaining.stream().anyMatch(log -> log.getDataSource().equals(DataSource.WALLBOX_OCPP)));
    }

    @Test
    void deleteAllImports_withNoImports_returnsSuccess() {
        // Given: User has no Sprit-Monitor imports
        createLog(testCarId, DataSource.USER_LOGGED);

        // When: Delete Sprit-Monitor imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/sprit-monitor/delete-all",
                HttpMethod.DELETE,
                request,
                Void.class
        );

        // Then: Success, no data deleted
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, evLogRepository.findAllByUserId(testUserId).size());
    }

    @Test
    void deleteAllImports_withoutAuth_returns401() {
        // When: Delete without authentication
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/sprit-monitor/delete-all",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: Unauthorized
        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
    }

    @Test
    void deleteAllImports_multipleCars_deletesAllFromAllCars() {
        // Given: User has multiple cars with Sprit-Monitor imports
        Car car2 = createAndSaveCar(testUserId, CarBrand.CarModel.MODEL_Y);
        UUID car2Id = car2.getId();

        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.USER_LOGGED);
        createLog(car2Id, DataSource.SPRITMONITOR_IMPORT);
        createLog(car2Id, DataSource.USER_LOGGED);

        assertEquals(4, evLogRepository.findAllByUserId(testUserId).size());

        // When: Delete all Sprit-Monitor imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        restTemplate.exchange("/api/import/sprit-monitor/delete-all", HttpMethod.DELETE, request, Void.class);

        // Then: All Sprit-Monitor imports from all cars deleted
        List<EvLog> remaining = evLogRepository.findAllByUserId(testUserId);
        assertEquals(2, remaining.size(), "Only USER_LOGGED from both cars should remain");
        assertTrue(remaining.stream().allMatch(log -> log.getDataSource().equals(DataSource.USER_LOGGED)));
    }

    @Test
    void deleteAllImports_alsoDeletesOrphanedSessionGroups() {
        // Given: User hat Spritmonitor-Logs und eine dazugehörige Session-Gruppe
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);

        // Session-Gruppe manuell anlegen (wie der Import es tun würde)
        ChargingSessionGroupEntity group = new ChargingSessionGroupEntity();
        group.setId(UUID.randomUUID());
        group.setCarId(testCarId);
        group.setTotalKwhCharged(new BigDecimal("51.02"));
        group.setSessionCount(2);
        group.setSessionStart(LocalDateTime.now());
        group.setSessionEnd(LocalDateTime.now().plusMinutes(80));
        group.setDataSource("SPRITMONITOR_IMPORT");
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        sessionGroupRepository.save(group);

        assertEquals(1, sessionGroupRepository.findAllByCarId(testCarId).size());

        // When: User löscht alle Spritmonitor-Imports
        HttpEntity<Void> request = createAuthRequest(testUserId, testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/sprit-monitor/delete-all", HttpMethod.DELETE, request, Void.class);

        // Then: Logs UND Gruppen gelöscht
        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, evLogRepository.findAllByUserId(testUserId).size(), "Alle Logs müssen gelöscht sein");
        assertEquals(0, sessionGroupRepository.findAllByCarId(testCarId).size(),
                "Waisen-Gruppen müssen ebenfalls gelöscht werden");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────────

    private void createLog(UUID carId, DataSource dataSource) {
        EvLog log = EvLog.createNewWithSource(
                carId,
                new BigDecimal("45.5"),
                new BigDecimal("18.20"),
                120,
                "u33d1",
                null,
                null,
                null,
                LocalDateTime.now(),
                dataSource,
                ChargingType.UNKNOWN,
                null
        );
        evLogRepository.save(log);
    }
}
