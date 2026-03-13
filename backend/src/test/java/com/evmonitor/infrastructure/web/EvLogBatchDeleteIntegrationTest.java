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
 * Critical Security Test: Verifies that DELETE /api/logs/batch
 * only deletes logs owned by the authenticated user.
 */
class EvLogBatchDeleteIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private User otherUser;
    private UUID testCarId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("batch-delete-" + System.nanoTime() + "@example.com");
        otherUser = createAndSaveUser("batch-other-" + System.nanoTime() + "@example.com");

        testCarId = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3).getId();
        otherCarId = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.ID_3).getId();
    }

    @Test
    void batchDelete_deletesOwnLogs() {
        EvLog log1 = createLog(testCarId, DataSource.TESLA_FLEET);
        EvLog log2 = createLog(testCarId, DataSource.TESLA_FLEET);

        HttpEntity<List<UUID>> request = createAuthRequest(
                List.of(log1.getId(), log2.getId()), testUser.getId(), testUser.getEmail());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/logs/batch", HttpMethod.DELETE, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(evLogRepository.findAllByUserId(testUser.getId()).isEmpty());
    }

    @Test
    void batchDelete_ignoresForeignIds_doesNotDelete() {
        EvLog foreignLog = createLog(otherCarId, DataSource.TESLA_FLEET);
        int countBefore = evLogRepository.findAllByUserId(otherUser.getId()).size();

        HttpEntity<List<UUID>> request = createAuthRequest(
                List.of(foreignLog.getId()), testUser.getId(), testUser.getEmail());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/logs/batch", HttpMethod.DELETE, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(countBefore, evLogRepository.findAllByUserId(otherUser.getId()).size(),
                "Foreign log MUST NOT be deleted");
    }

    @Test
    void batchDelete_mixedIds_onlyDeletesOwn() {
        EvLog ownLog = createLog(testCarId, DataSource.TESLA_FLEET);
        EvLog foreignLog = createLog(otherCarId, DataSource.TESLA_FLEET);

        HttpEntity<List<UUID>> request = createAuthRequest(
                List.of(ownLog.getId(), foreignLog.getId()), testUser.getId(), testUser.getEmail());

        restTemplate.exchange("/api/logs/batch", HttpMethod.DELETE, request, Void.class);

        assertTrue(evLogRepository.findAllByUserId(testUser.getId()).isEmpty(),
                "Own log must be deleted");
        assertEquals(1, evLogRepository.findAllByUserId(otherUser.getId()).size(),
                "Foreign log MUST NOT be deleted");
    }

    @Test
    void batchDelete_withUnknownId_returnsNoContent() {
        HttpEntity<List<UUID>> request = createAuthRequest(
                List.of(UUID.randomUUID()), testUser.getId(), testUser.getEmail());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/logs/batch", HttpMethod.DELETE, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void batchDelete_withoutAuth_returns401() {
        HttpEntity<List<UUID>> request = new HttpEntity<>(List.of(UUID.randomUUID()));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/logs/batch", HttpMethod.DELETE, request, Void.class);

        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
    }

    private EvLog createLog(UUID carId, DataSource dataSource) {
        return evLogRepository.save(EvLog.createNewWithSource(
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
