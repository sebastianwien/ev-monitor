package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DELETE /api/logs/{id} ist ein Soft-Delete, POST /api/logs/{id}/restore macht ihn rückgängig.
 * Ownership: fremde Logs sind weder löschbar noch wiederherstellbar.
 */
class EvLogRestoreIntegrationTest extends AbstractIntegrationTest {

    private User owner;
    private User stranger;
    private UUID carId;

    @BeforeEach
    void setUp() {
        owner = createAndSaveUser("restore-" + System.nanoTime() + "@example.com");
        stranger = createAndSaveUser("restore-other-" + System.nanoTime() + "@example.com");
        carId = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void deleteThenRestore_roundTrip() {
        EvLog log = createLog();

        ResponseEntity<Void> del = restTemplate.exchange("/api/logs/" + log.getId(), HttpMethod.DELETE,
                createAuthRequest(owner.getId(), owner.getEmail()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, del.getStatusCode());
        assertTrue(evLogRepository.findById(log.getId()).isEmpty(), "gelöscht: unsichtbar");
        assertTrue(evLogRepository.findByIdIncludingDeleted(log.getId()).isPresent(), "gelöscht: Tombstone bleibt");

        ResponseEntity<Void> restore = restTemplate.exchange("/api/logs/" + log.getId() + "/restore", HttpMethod.POST,
                createAuthRequest(owner.getId(), owner.getEmail()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, restore.getStatusCode());
        assertTrue(evLogRepository.findById(log.getId()).isPresent(), "wiederhergestellt: sichtbar");
    }

    @Test
    void restore_foreignLog_returns403AndStaysDeleted() {
        EvLog log = createLog();
        evLogRepository.softDelete(log.getId());

        ResponseEntity<Void> restore = restTemplate.exchange("/api/logs/" + log.getId() + "/restore", HttpMethod.POST,
                createAuthRequest(stranger.getId(), stranger.getEmail()), Void.class);

        assertEquals(HttpStatus.FORBIDDEN, restore.getStatusCode());
        assertTrue(evLogRepository.findById(log.getId()).isEmpty());
    }

    @Test
    void restore_notDeleted_returns404() {
        EvLog log = createLog();

        ResponseEntity<Void> restore = restTemplate.exchange("/api/logs/" + log.getId() + "/restore", HttpMethod.POST,
                createAuthRequest(owner.getId(), owner.getEmail()), Void.class);

        assertEquals(HttpStatus.NOT_FOUND, restore.getStatusCode());
    }

    @Test
    void restore_unknownId_returns404() {
        ResponseEntity<Void> restore = restTemplate.exchange("/api/logs/" + UUID.randomUUID() + "/restore", HttpMethod.POST,
                createAuthRequest(owner.getId(), owner.getEmail()), Void.class);

        assertEquals(HttpStatus.NOT_FOUND, restore.getStatusCode());
    }

    @Test
    void restore_withoutAuth_isRejected() {
        ResponseEntity<Void> restore = restTemplate.exchange("/api/logs/" + UUID.randomUUID() + "/restore", HttpMethod.POST,
                new HttpEntity<>(null), Void.class);

        assertTrue(restore.getStatusCode().value() == 401 || restore.getStatusCode().value() == 403);
    }

    @Test
    void deletedLog_isAbsentFromCarListing() {
        EvLog log = createLog();
        evLogRepository.softDelete(log.getId());

        ResponseEntity<String> list = restTemplate.exchange("/api/logs?carId=" + carId, HttpMethod.GET,
                createAuthRequest(owner.getId(), owner.getEmail()), String.class);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertTrue(list.getBody() == null || !list.getBody().contains(log.getId().toString()));
    }

    private EvLog createLog() {
        return evLogRepository.save(EvLog.createNewWithSource(
                carId, new BigDecimal("30.0"), new BigDecimal("12.00"), 60, "u33d1",
                null, null, null,
                LocalDateTime.now().minusMinutes((long) (Math.random() * 10000)),
                DataSource.USER_LOGGED, ChargingType.UNKNOWN, null));
    }
}
