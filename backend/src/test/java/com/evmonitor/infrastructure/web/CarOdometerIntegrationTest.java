package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarOdometerResponse;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /api/cars/{id}/odometer: neuester Tachostand aus Ladevorgängen und Fahrten.
 * Neuester, nicht höchster: ein alter Tippfehler nach oben darf den Stand nicht verfälschen.
 */
class CarOdometerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EvTripRepository evTripRepository;

    private User user;
    private UUID carId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("odo-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        carId = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3).getId();
    }

    private EvLog log(LocalDateTime at, Integer odometerKm) {
        return evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("30.0"), null, 60, "u33d1", odometerKm,
                new BigDecimal("11.0"), new BigDecimal("80"), at, DataSource.USER_LOGGED, ChargingType.AC, null));
    }

    private void trip(OffsetDateTime end, String odometerEndKm) {
        evTripRepository.save(EvTrip.builder().userId(user.getId()).carId(carId).dataSource(EvTrip.DATA_SOURCE_TESLA_LIVE)
                .tripStartedAt(end.minusMinutes(30)).tripEndedAt(end)
                .odometerEndKm(new BigDecimal(odometerEndKm)).distanceKm(new BigDecimal("20"))
                .status("COMPLETED").createdAt(OffsetDateTime.now()).build());
    }

    private ResponseEntity<CarOdometerResponse> get(UUID car, User as) {
        return restTemplate.exchange("/api/cars/" + car + "/odometer", HttpMethod.GET,
                createAuthRequest(as.getId(), as.getEmail()), CarOdometerResponse.class);
    }

    @Test
    void newestReadingWinsOverHigherOlderOne() {
        log(LocalDateTime.of(2026, 1, 10, 8, 0), 66_292);
        log(LocalDateTime.of(2026, 3, 1, 8, 0), 49_100);
        log(LocalDateTime.of(2026, 4, 1, 8, 0), 49_217);

        ResponseEntity<CarOdometerResponse> res = get(carId, user);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(49_217, res.getBody().currentOdometerKm());
    }

    @Test
    void newerTripBeatsOlderLog() {
        log(LocalDateTime.of(2026, 10, 1, 8, 0), 66_113);
        trip(OffsetDateTime.of(2026, 10, 3, 17, 0, 0, 0, ZoneOffset.UTC), "66182.6");

        assertEquals(66_183, get(carId, user).getBody().currentOdometerKm());
    }

    @Test
    void ignoresDeletedLogsAndPlaceholders() {
        log(LocalDateTime.of(2026, 9, 1, 8, 0), 12_000);
        EvLog deleted = log(LocalDateTime.of(2026, 9, 5, 8, 0), 99_999);
        evLogRepository.softDelete(deleted.getId());
        log(LocalDateTime.of(2026, 9, 6, 8, 0), 0);
        log(LocalDateTime.of(2026, 9, 7, 8, 0), null);

        assertEquals(12_000, get(carId, user).getBody().currentOdometerKm());
    }

    @Test
    void noReadingGivesNull() {
        ResponseEntity<CarOdometerResponse> res = get(carId, user);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNull(res.getBody().currentOdometerKm());
    }

    @Test
    void foreignCarIsForbidden() {
        log(LocalDateTime.of(2026, 9, 1, 8, 0), 12_000);
        User other = createAndSaveUser("odo-other-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        ResponseEntity<String> res = restTemplate.exchange("/api/cars/" + carId + "/odometer", HttpMethod.GET,
                createAuthRequest(other.getId(), other.getEmail()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertFalse(res.getBody() != null && res.getBody().contains("12000"));
    }

    @Test
    void unauthenticatedIsRejected() {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/cars/" + carId + "/odometer", String.class);

        assertTrue(res.getStatusCode().is4xxClientError());
    }
}
