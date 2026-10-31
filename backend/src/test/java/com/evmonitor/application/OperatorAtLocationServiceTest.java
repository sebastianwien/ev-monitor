package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Betreiber an einem Ort auf die anderen Ladungen ohne Betreiber übertragen - nur wenn der
 * Ort eindeutig ist: öffentliche Präzision (7 Zeichen, ~150 m) und kein anderer Betreiber dort.
 */
class OperatorAtLocationServiceTest extends AbstractIntegrationTest {

    private static final String LOCATION = "u1mc1v8";
    private static final String OTHER_LOCATION = "u1m9r98";

    @Autowired
    private OperatorAtLocationService service;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        userId = createAndSaveUser("op-" + System.nanoTime() + "@example.com").getId();
        carId = saveCar(userId);
    }

    private UUID saveCar(UUID owner) {
        Car car = Car.createNew(owner, CarBrand.CarModel.MODEL_3, 2023, "OP-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        return car.getId();
    }

    private EvLog saveLog(UUID car, String geohash, Boolean isPublic, String cpoName) {
        return evLogRepository.save(EvLog.createNew(car, new BigDecimal("30.0"), null, 45, geohash, 10_000,
                new BigDecimal("150.0"), new BigDecimal("80.0"), LocalDateTime.now().minusDays(3),
                ChargingType.DC, null, null, isPublic, cpoName));
    }

    @Test
    void countsOtherPublicLogsWithoutOperatorAtExactGeohash() {
        EvLog edited = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, "  ");
        saveLog(carId, LOCATION, false, null);          // privat: nie
        saveLog(carId, OTHER_LOCATION, true, null);     // anderer Ort
        saveLog(saveCar(createAndSaveUser("x-" + System.nanoTime() + "@example.com").getId()), LOCATION, true, null); // fremder User

        assertEquals(2, service.countOperatorGaps(userId, LOCATION, "IONITY", edited.getId()));
    }

    @Test
    void ambiguousWhenAnotherOperatorAlreadyChargedHere() {
        EvLog edited = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, "EnBW");

        assertEquals(0, service.countOperatorGaps(userId, LOCATION, "IONITY", edited.getId()));
    }

    @Test
    void sameOperatorAlreadyHereIsNoConflict() {
        EvLog edited = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, "ionity ");

        assertEquals(1, service.countOperatorGaps(userId, LOCATION, "IONITY", edited.getId()));
    }

    @Test
    void editedLogsOldOperatorDoesNotBlock() {
        EvLog edited = saveLog(carId, LOCATION, true, "EnBW");
        saveLog(carId, LOCATION, true, null);

        assertEquals(1, service.countOperatorGaps(userId, LOCATION, "IONITY", edited.getId()));
    }

    @Test
    void privatePrecisionIsNeverUnique() {
        EvLog edited = saveLog(carId, "u1mc1v", true, null);
        saveLog(carId, "u1mc1v", true, null);

        assertEquals(0, service.countOperatorGaps(userId, "u1mc1v", "IONITY", edited.getId()));
    }

    @Test
    void blankOperatorCountsNothing() {
        EvLog edited = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, null);

        assertEquals(0, service.countOperatorGaps(userId, LOCATION, " ", edited.getId()));
    }

    @Test
    void oversizedInputCountsNothing() {
        EvLog edited = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, null);

        assertEquals(0, service.countOperatorGaps(userId, LOCATION, "x".repeat(101), edited.getId()));
        assertEquals(0, service.countOperatorGaps(userId, LOCATION + "x".repeat(10), "IONITY", edited.getId()));
    }

    @Test
    void appliesOperatorOnlyToGaps() {
        EvLog edited = saveLog(carId, LOCATION, true, "IONITY");
        EvLog gap = saveLog(carId, LOCATION, true, null);
        EvLog privateLog = saveLog(carId, LOCATION, false, null);
        EvLog elsewhere = saveLog(carId, OTHER_LOCATION, true, null);

        OperatorAtLocationService.OperatorApplied applied = service.applyOperator(userId, LOCATION, "IONITY", edited.getId());

        assertEquals(1, applied.updated());
        assertTrue(applied.coinsAwarded() > 0);
        assertEquals("IONITY", evLogRepository.findById(gap.getId()).orElseThrow().getCpoName());
        assertNull(evLogRepository.findById(privateLog.getId()).orElseThrow().getCpoName());
        assertNull(evLogRepository.findById(elsewhere.getId()).orElseThrow().getCpoName());
    }

    @Test
    void applyRefusesAmbiguousLocation() {
        EvLog edited = saveLog(carId, LOCATION, true, "IONITY");
        EvLog gap = saveLog(carId, LOCATION, true, null);
        saveLog(carId, LOCATION, true, "EnBW");

        assertEquals(0, service.applyOperator(userId, LOCATION, "IONITY", edited.getId()).updated());
        assertNull(evLogRepository.findById(gap.getId()).orElseThrow().getCpoName());
    }

    @Test
    void applyNeverTouchesOtherUsersLogs() {
        UUID otherUser = createAndSaveUser("y-" + System.nanoTime() + "@example.com").getId();
        EvLog foreign = saveLog(saveCar(otherUser), LOCATION, true, null);
        EvLog edited = saveLog(carId, LOCATION, true, "IONITY");

        service.applyOperator(userId, LOCATION, "IONITY", edited.getId());

        assertNull(evLogRepository.findById(foreign.getId()).orElseThrow().getCpoName());
    }
}
