package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teilen einer Fahrzeugseite: Ownership, Idempotenz, Widerruf und vor allem,
 * dass die oeffentliche Antwort weder Standort noch Besitzer verraet.
 */
class CarShareServiceTest extends AbstractIntegrationTest {

    @Autowired private CarShareService shareService;
    @Autowired private CarRepository carRepository;
    @Autowired private EvLogRepository evLogRepository;
    @Autowired private com.evmonitor.application.user.AccountAnonymizationService anonymizationService;

    @Test
    void createShare_owner_returnsTokenAndMakesCarPublic() {
        User user = createAndSaveUser("carshare-ok-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        saveLog(car.getId(), LocalDateTime.now().minusDays(2), 10_000);
        saveLog(car.getId(), LocalDateTime.now().minusDays(1), 10_300);

        ShareResponse share = shareService.createShare(car.getId(), user);

        assertNotNull(share.token());
        assertTrue(share.token().length() >= 10, "Token muss lang genug sein um nicht ratbar zu sein");
        assertTrue(share.url().endsWith("/fahrzeug/" + share.token()));

        PublicCarResponse pub = shareService.getPublicCar(share.token()).orElseThrow();
        assertEquals("Tesla Model 3", pub.carModel());
        assertEquals(2, pub.totalCharges());
        assertEquals(2, pub.recentCharges().size());
    }

    @Test
    void createShare_isIdempotent() {
        User user = createAndSaveUser("carshare-idem-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        assertEquals(shareService.createShare(car.getId(), user).token(),
                shareService.createShare(car.getId(), user).token());
    }

    @Test
    void createShare_nonOwner_throws() {
        User owner = createAndSaveUser("carshare-owner-" + System.nanoTime() + "@test.com");
        User intruder = createAndSaveUser("carshare-intruder-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);

        assertThrows(IllegalArgumentException.class, () -> shareService.createShare(car.getId(), intruder));
        assertThrows(IllegalArgumentException.class, () -> shareService.revokeShare(car.getId(), intruder));
        assertThrows(IllegalArgumentException.class, () -> shareService.findShare(car.getId(), intruder));
    }

    @Test
    void revokeShare_killsTokenAndReshareGivesNewOne() {
        User user = createAndSaveUser("carshare-revoke-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String first = shareService.createShare(car.getId(), user).token();

        shareService.revokeShare(car.getId(), user);

        assertTrue(shareService.getPublicCar(first).isEmpty(), "alter Link muss tot sein");
        assertTrue(shareService.findShare(car.getId(), user).isEmpty());
        String second = shareService.createShare(car.getId(), user).token();
        assertNotEquals(first, second, "erneutes Teilen darf den alten Link nicht wiederbeleben");
    }

    @Test
    void shareToken_survivesCarUpdate() {
        // Ein normales Speichern des Autos (z.B. Kennzeichen aendern) darf die
        // Freigabe nicht stillschweigend loeschen.
        User user = createAndSaveUser("carshare-persist-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String token = shareService.createShare(car.getId(), user).token();

        carRepository.save(carRepository.findById(car.getId()).orElseThrow().toBuilder().licensePlate("NEU-1").build());

        assertTrue(shareService.getPublicCar(token).isPresent());
    }

    @Test
    void accountAnonymization_killsShareLink() {
        // DSGVO: nach der Kontoloeschung darf kein oeffentlicher Link mehr Daten liefern.
        User user = createAndSaveUser("carshare-anon-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String token = shareService.createShare(car.getId(), user).token();

        anonymizationService.anonymizeCarsOf(user.getId());

        assertTrue(shareService.getPublicCar(token).isEmpty());
    }

    @Test
    void getPublicCar_unknownToken_isEmpty() {
        assertTrue(shareService.getPublicCar("nichtvorhanden").isEmpty());
        assertTrue(shareService.getPublicCar("").isEmpty());
        assertTrue(shareService.getPublicCar(null).isEmpty());
    }

    @Test
    void getPublicCar_exposesOnlyPublicFields() {
        User user = createAndSaveUser("carshare-fields-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        saveLog(car.getId(), LocalDateTime.now().minusDays(1), 10_000);
        String token = shareService.createShare(car.getId(), user).token();

        PublicCarResponse pub = shareService.getPublicCar(token).orElseThrow();

        String json = pub.toString();
        assertFalse(json.contains("geohash"), "Ort darf nicht exponiert werden");
        assertFalse(json.contains("odometer"), "Kilometerstand darf nicht exponiert werden");
        assertFalse(json.contains("cpo"), "Betreiber verraet den Ort");
        assertFalse(json.contains(user.getId().toString()), "Besitzer darf nicht exponiert werden");
        assertFalse(json.contains(user.getEmail()), "E-Mail darf nicht exponiert werden");
        assertFalse(json.contains("licensePlate"), "Kennzeichen darf nicht exponiert werden");
        assertNotNull(pub.recentCharges().get(0).chargedOn(), "Datum ja, Uhrzeit nicht");
    }

    private EvLog saveLog(UUID carId, LocalDateTime loggedAt, int odometerKm) {
        EvLog log = EvLog.createFromInternal(
                carId, new BigDecimal("43.8"), 40, "u1hcv8", loggedAt,
                null, null, DataSource.USER_LOGGED, new BigDecimal("18.50"), ChargingType.AC,
                odometerKm, new BigDecimal("20"), new BigDecimal("80"), null, null,
                true, "IONITY");
        return evLogRepository.save(log);
    }
}
