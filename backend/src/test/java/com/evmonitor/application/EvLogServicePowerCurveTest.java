package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: EvLogService.getPowerCurveForUser - ownership gate, paid-analytics
 * gate (historical curves stay premium for all brands) + JSON round-trip.
 */
class EvLogServicePowerCurveTest extends AbstractIntegrationTest {

    @Autowired private EvLogService evLogService;
    @Autowired private EvLogRepository evLogRepository;

    @Test
    void getPowerCurve_entitledOwner_logWithCurve_returnsParsedPoints() {
        User user = createAndSaveAutoSyncLiveUser("pc-curve-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.TESLA_LIVE);
        // Mimic what the connectors-side push writes into the JSONB column.
        evLogRepository.updatePowerCurvePoints(log.getId(),
                "[{\"ts\":1715515200000,\"kw\":42.5},{\"ts\":1715515260000,\"kw\":150.0}]");

        PowerCurveResponse res = evLogService.getPowerCurveForUser(log.getId(), user);

        assertEquals(2, res.points().size());
        assertEquals(1715515200000L, res.points().get(0).ts());
        assertEquals(42.5, res.points().get(0).kw());
        assertEquals(150.0, res.points().get(1).kw());
    }

    @Test
    void getPowerCurve_curveWithSoc_returnsSocPerPoint() {
        User user = createAndSaveAutoSyncLiveUser("pc-soc-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.TESLA_LIVE);
        evLogRepository.updatePowerCurvePoints(log.getId(),
                "[{\"ts\":1715515200000,\"kw\":42.5,\"soc\":18.5},{\"ts\":1715515260000,\"kw\":150.0,\"soc\":24.0}]");

        PowerCurveResponse res = evLogService.getPowerCurveForUser(log.getId(), user);

        assertEquals(2, res.points().size());
        assertEquals(18.5, res.points().get(0).soc());
        assertEquals(24.0, res.points().get(1).soc());
    }

    @Test
    void getPowerCurve_legacyCurveWithoutSoc_parsesWithNullSoc() {
        // Kurven aus der Zeit vor der SoC-Anreicherung muessen weiter lesbar bleiben -
        // das Frontend leitet den Verlauf dann aus der kumulierten Energie ab.
        User user = createAndSaveAutoSyncLiveUser("pc-legacy-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.TESLA_LIVE);
        evLogRepository.updatePowerCurvePoints(log.getId(),
                "[{\"ts\":1715515200000,\"kw\":42.5}]");

        PowerCurveResponse res = evLogService.getPowerCurveForUser(log.getId(), user);

        assertEquals(1, res.points().size());
        assertNull(res.points().get(0).soc());
    }

    @Test
    void getPowerCurve_nonEntitledOwner_logWithCurve_returnsEmpty() {
        // Historical curves stay premium even for Tesla: a free owner with a persisted
        // curve gets an empty response (the gate), not the points.
        User user = createAndSaveUser("pc-free-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.TESLA_LIVE);
        evLogRepository.updatePowerCurvePoints(log.getId(),
                "[{\"ts\":1715515200000,\"kw\":42.5},{\"ts\":1715515260000,\"kw\":150.0}]");

        PowerCurveResponse res = evLogService.getPowerCurveForUser(log.getId(), user);

        assertTrue(res.points().isEmpty());
    }

    @Test
    void getPowerCurve_entitledOwner_logWithoutCurve_returnsEmptyList() {
        User user = createAndSaveAutoSyncLiveUser("pc-empty-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.WALLBOX_OCPP);

        PowerCurveResponse res = evLogService.getPowerCurveForUser(log.getId(), user);

        assertTrue(res.points().isEmpty());
    }

    @Test
    void getPowerCurve_nonOwner_throws() {
        User owner = createAndSaveAutoSyncLiveUser("pc-owner-" + System.nanoTime() + "@test.com");
        User intruder = createAndSaveAutoSyncLiveUser("pc-intruder-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWith(car.getId(), DataSource.TESLA_LIVE);
        evLogRepository.updatePowerCurvePoints(log.getId(),
                "[{\"ts\":1715515200000,\"kw\":42.5}]");

        // Ownership is checked before the analytics gate, so a non-owner gets 404 even
        // though they hold the entitlement.
        assertThrows(IllegalArgumentException.class,
                () -> evLogService.getPowerCurveForUser(log.getId(), intruder));
    }

    @Test
    void getPowerCurve_unknownLogId_throws() {
        User user = createAndSaveAutoSyncLiveUser("pc-unknown-" + System.nanoTime() + "@test.com");
        assertThrows(IllegalArgumentException.class,
                () -> evLogService.getPowerCurveForUser(UUID.randomUUID(), user));
    }

    private EvLog saveLogWith(UUID carId, DataSource source) {
        EvLog log = EvLog.createFromInternal(
                carId,
                new BigDecimal("10.0"),
                30,
                null,
                LocalDateTime.now().minusHours(1),
                null, null,
                source,
                null, null,
                null, null, null, null, null, null, null);
        return evLogRepository.save(log);
    }
}
