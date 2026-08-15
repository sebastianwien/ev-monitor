package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: Teilen einer Ladekurve. Deckt das Opt-in pro Ladung ab -
 * Ownership, Entitlement, Widerruf und vor allem, dass der oeffentliche Lookup
 * nur die Ladung hinter dem jeweiligen Token herausgibt.
 */
class EvLogShareServiceTest extends AbstractIntegrationTest {

    private static final String CURVE_JSON =
            "[{\"ts\":1715515200000,\"kw\":42.5,\"soc\":18.5},{\"ts\":1715515260000,\"kw\":150.0,\"soc\":24.0}]";

    @Autowired private EvLogShareService shareService;
    @Autowired private EvLogRepository evLogRepository;

    @Test
    void createShare_entitledOwner_returnsTokenAndMakesCurvePublic() {
        User user = createAndSaveAutoSyncLiveUser("share-ok-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        ShareResponse share = shareService.createShare(log.getId(), user);

        assertNotNull(share.token());
        assertTrue(share.token().length() >= 10, "Token muss lang genug sein um nicht ratbar zu sein");
        assertTrue(share.url().endsWith(share.token()));

        PublicCurveResponse pub = shareService.getPublicCurve(share.token()).orElseThrow();
        assertEquals(2, pub.points().size());
        assertEquals("Tesla Model 3", pub.carModel());
    }

    @Test
    void createShare_isIdempotent_sameTokenOnSecondCall() {
        // Zweimal Teilen darf keine zweite URL erzeugen - sonst bleiben tote
        // Links im Umlauf, die der Nutzer nicht mehr widerrufen kann.
        User user = createAndSaveAutoSyncLiveUser("share-idem-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        String first = shareService.createShare(log.getId(), user).token();
        String second = shareService.createShare(log.getId(), user).token();

        assertEquals(first, second);
    }

    @Test
    void createShare_twoLogs_getDistinctTokens() {
        User user = createAndSaveAutoSyncLiveUser("share-distinct-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog a = saveLogWithCurve(car.getId());
        EvLog b = saveLogWithCurve(car.getId(), LocalDateTime.now().minusHours(5));

        assertNotEquals(shareService.createShare(a.getId(), user).token(),
                shareService.createShare(b.getId(), user).token());
    }

    @Test
    void createShare_nonOwner_throws() {
        User owner = createAndSaveAutoSyncLiveUser("share-owner-" + System.nanoTime() + "@test.com");
        User intruder = createAndSaveAutoSyncLiveUser("share-intruder-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        assertThrows(IllegalArgumentException.class,
                () -> shareService.createShare(log.getId(), intruder));
    }

    @Test
    void createShare_nonEntitledOwner_denied() {
        // Wer die eigene Kurve nicht sehen darf, kann sie auch nicht teilen -
        // sonst waere der oeffentliche Link ein Weg am Bezahl-Gate vorbei.
        User user = createAndSaveUser("share-free-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        assertThrows(AccessDeniedException.class,
                () -> shareService.createShare(log.getId(), user));
    }

    @Test
    void createShare_logWithoutCurve_throws() {
        User user = createAndSaveAutoSyncLiveUser("share-nocurve-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLog(car.getId(), LocalDateTime.now().minusHours(1));

        assertThrows(IllegalStateException.class,
                () -> shareService.createShare(log.getId(), user));
    }

    @Test
    void revokeShare_makesTokenDead() {
        User user = createAndSaveAutoSyncLiveUser("share-revoke-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String token = shareService.createShare(log.getId(), user).token();

        shareService.revokeShare(log.getId(), user);

        assertTrue(shareService.getPublicCurve(token).isEmpty());
    }

    @Test
    void revokeShare_thenShareAgain_yieldsNewToken() {
        // Nach dem Widerruf darf der alte Link nicht wieder aufleben.
        User user = createAndSaveAutoSyncLiveUser("share-reshare-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String old = shareService.createShare(log.getId(), user).token();

        shareService.revokeShare(log.getId(), user);
        String fresh = shareService.createShare(log.getId(), user).token();

        assertNotEquals(old, fresh);
        assertTrue(shareService.getPublicCurve(old).isEmpty());
        assertTrue(shareService.getPublicCurve(fresh).isPresent());
    }

    @Test
    void revokeShare_nonOwner_throws() {
        User owner = createAndSaveAutoSyncLiveUser("share-rvowner-" + System.nanoTime() + "@test.com");
        User intruder = createAndSaveAutoSyncLiveUser("share-rvintr-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String token = shareService.createShare(log.getId(), owner).token();

        assertThrows(IllegalArgumentException.class,
                () -> shareService.revokeShare(log.getId(), intruder));
        assertTrue(shareService.getPublicCurve(token).isPresent(), "Fremder Widerruf darf nichts bewirken");
    }

    @Test
    void getPublicCurve_unknownToken_isEmpty() {
        assertTrue(shareService.getPublicCurve("nichtvorhanden").isEmpty());
    }

    @Test
    void getPublicCurve_exposesOnlyPublicFields() {
        // Der Kilometerstand und der Ort duerfen die oeffentliche Antwort nicht
        // erreichen - das DTO hat dafuer gar keine Felder, dieser Test haelt das fest.
        User user = createAndSaveAutoSyncLiveUser("share-fields-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String token = shareService.createShare(log.getId(), user).token();

        PublicCurveResponse pub = shareService.getPublicCurve(token).orElseThrow();

        String json = pub.toString();
        assertFalse(json.contains("odometer"), "Kilometerstand darf nicht exponiert werden");
        assertFalse(json.contains("geohash"), "Ort darf nicht exponiert werden");
        assertNotNull(pub.chargedOn(), "Datum als Kontext ja - Uhrzeit nicht");
    }

    private EvLog saveLogWithCurve(UUID carId) {
        return saveLogWithCurve(carId, LocalDateTime.now().minusHours(1));
    }

    private EvLog saveLogWithCurve(UUID carId, LocalDateTime loggedAt) {
        EvLog log = saveLog(carId, loggedAt);
        evLogRepository.updatePowerCurvePoints(log.getId(), CURVE_JSON);
        return log;
    }

    private EvLog saveLog(UUID carId, LocalDateTime loggedAt) {
        EvLog log = EvLog.createFromInternal(
                carId,
                new BigDecimal("43.8"),
                17,
                null,
                loggedAt,
                null, null,
                DataSource.TESLA_LIVE,
                null, null,
                null, null, null, null, null, null, null);
        return evLogRepository.save(log);
    }
}
