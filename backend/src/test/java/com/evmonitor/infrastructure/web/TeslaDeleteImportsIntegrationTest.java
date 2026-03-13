package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical Safety Test: Verifies that DELETE /api/import/tesla/delete-all
 * only deletes TESLA_FLEET and TESLA_HOME logs for the authenticated user,
 * and does NOT touch USER_LOGGED entries or other users' data.
 */
class TeslaDeleteImportsIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private User otherUser;
    private UUID testCarId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("tesla-delete-" + System.nanoTime() + "@example.com");
        otherUser = createAndSaveUser("tesla-other-" + System.nanoTime() + "@example.com");

        testCarId = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3).getId();
        otherCarId = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.ID_3).getId();
    }

    @Test
    void deleteAll_onlyDeletesTeslaFleetAndHome_notUserLogged() {
        createLog(testCarId, DataSource.TESLA_FLEET);
        createLog(testCarId, DataSource.TESLA_HOME);
        createLog(testCarId, DataSource.USER_LOGGED);
        createLog(testCarId, DataSource.USER_LOGGED);

        assertEquals(4, evLogRepository.findAllByUserId(testUser.getId()).size());

        HttpEntity<Void> request = createAuthRequest(testUser.getId(), testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/tesla/delete-all", HttpMethod.DELETE, request, Void.class);

        assertEquals(200, response.getStatusCode().value());
        List<EvLog> remaining = evLogRepository.findAllByUserId(testUser.getId());
        assertEquals(2, remaining.size(), "Only USER_LOGGED entries should remain");
        assertTrue(remaining.stream().allMatch(l -> l.getDataSource() == DataSource.USER_LOGGED));
    }

    @Test
    void deleteAll_onlyDeletesOwnData_notOtherUsers() {
        createLog(testCarId, DataSource.TESLA_FLEET);
        createLog(otherCarId, DataSource.TESLA_FLEET);

        HttpEntity<Void> request = createAuthRequest(testUser.getId(), testUser.getEmail());
        restTemplate.exchange("/api/import/tesla/delete-all", HttpMethod.DELETE, request, Void.class);

        assertTrue(evLogRepository.findAllByUserId(testUser.getId()).isEmpty());
        assertEquals(1, evLogRepository.findAllByUserId(otherUser.getId()).size(),
                "Other user's data MUST NOT be affected");
    }

    @Test
    void deleteAll_doesNotDeleteSpritMonitorOrWallbox() {
        createLog(testCarId, DataSource.TESLA_FLEET);
        createLog(testCarId, DataSource.TESLA_HOME);
        createLog(testCarId, DataSource.SPRITMONITOR_IMPORT);
        createLog(testCarId, DataSource.WALLBOX_OCPP);

        HttpEntity<Void> request = createAuthRequest(testUser.getId(), testUser.getEmail());
        restTemplate.exchange("/api/import/tesla/delete-all", HttpMethod.DELETE, request, Void.class);

        List<EvLog> remaining = evLogRepository.findAllByUserId(testUser.getId());
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(l -> l.getDataSource() == DataSource.SPRITMONITOR_IMPORT));
        assertTrue(remaining.stream().anyMatch(l -> l.getDataSource() == DataSource.WALLBOX_OCPP));
    }

    @Test
    void deleteAll_withNoImports_returnsSuccess() {
        createLog(testCarId, DataSource.USER_LOGGED);

        HttpEntity<Void> request = createAuthRequest(testUser.getId(), testUser.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/tesla/delete-all", HttpMethod.DELETE, request, Void.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, evLogRepository.findAllByUserId(testUser.getId()).size());
    }

    @Test
    void deleteAll_withoutAuth_returns401() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/tesla/delete-all", HttpMethod.DELETE, null, Void.class);

        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
    }

    private void createLog(UUID carId, DataSource dataSource) {
        evLogRepository.save(EvLog.createNewWithSource(
                carId,
                new BigDecimal("45.5"),
                new BigDecimal("18.20"),
                120,
                "u33d1",
                null, null, null,
                LocalDateTime.now().minusMinutes((long) (Math.random() * 10000)),
                dataSource,
                ChargingType.UNKNOWN,
                null
        ));
    }
}
