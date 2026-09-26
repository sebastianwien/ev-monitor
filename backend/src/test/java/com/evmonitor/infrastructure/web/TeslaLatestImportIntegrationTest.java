package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /api/import/tesla/latest: Zeitpunkt der letzten Tesla-Ladung und -Fahrt des eigenen Users,
 * als Lebenszeichen der Sync-Strecke.
 */
class TeslaLatestImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EvTripRepository tripRepository;

    private User user;
    private User otherUser;
    private UUID carId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("tesla-latest-" + System.nanoTime() + "@example.com");
        otherUser = createAndSaveUser("tesla-latest-other-" + System.nanoTime() + "@example.com");
        carId = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3).getId();
        otherCarId = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void returnsNewestTeslaChargeAndTrip_ignoringOtherSourcesAndUsers() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 20, 10, 0);
        createLog(carId, DataSource.TESLA_LIVE, base);
        createLog(carId, DataSource.TESLA_FLEET_IMPORT, base.plusHours(2));
        createLog(carId, DataSource.USER_LOGGED, base.plusDays(1));
        createLog(otherCarId, DataSource.TESLA_LIVE, base.plusDays(2));

        OffsetDateTime tripEnd = OffsetDateTime.of(2026, 9, 21, 8, 30, 0, 0, ZoneOffset.UTC);
        createTrip(user.getId(), carId, "TESLA_LIVE", tripEnd.minusDays(1));
        createTrip(user.getId(), carId, "TESLA_INFERRED", tripEnd);
        createTrip(user.getId(), carId, "SMARTCAR_LIVE", tripEnd.plusDays(1));
        createTrip(otherUser.getId(), otherCarId, "TESLA_LIVE", tripEnd.plusDays(2));

        ResponseEntity<Map> response = restTemplate.exchange("/api/import/tesla/latest", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Instant.parse("2026-09-20T12:00:00Z"), Instant.parse((String) response.getBody().get("lastChargeAt")));
        assertEquals(tripEnd.toInstant(), Instant.parse((String) response.getBody().get("lastTripAt")));
    }

    @Test
    void returnsNullsWithoutTeslaData() {
        createLog(carId, DataSource.USER_LOGGED, LocalDateTime.now());

        ResponseEntity<Map> response = restTemplate.exchange("/api/import/tesla/latest", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().get("lastChargeAt"));
        assertNull(response.getBody().get("lastTripAt"));
    }

    @Test
    void withoutAuth_isRejected() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/import/tesla/latest", HttpMethod.GET, null, Void.class);
        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
    }

    private void createLog(UUID carId, DataSource dataSource, LocalDateTime loggedAt) {
        evLogRepository.save(EvLog.createNewWithSource(
                carId, new BigDecimal("30.0"), new BigDecimal("10.00"), 60, "u33d1",
                null, null, null, loggedAt, dataSource, ChargingType.UNKNOWN, null));
    }

    private void createTrip(UUID userId, UUID carId, String source, OffsetDateTime endedAt) {
        tripRepository.save(EvTrip.builder()
                .userId(userId).carId(carId)
                .dataSource(source).status("COMPLETED").userCreated(false)
                .tripStartedAt(endedAt.minusHours(1)).tripEndedAt(endedAt)
                .distanceKm(new BigDecimal("20.0"))
                .build());
    }
}
