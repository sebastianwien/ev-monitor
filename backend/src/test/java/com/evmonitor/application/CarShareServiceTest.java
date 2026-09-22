package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
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
    @Autowired private VehicleSpecificationRepository vehicleSpecificationRepository;
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
        assertTrue(share.url().contains("/fahrzeug/" + share.token()), share.url());

        PublicCarResponse pub = shareService.getPublicCar(share.token()).orElseThrow();
        assertEquals("Tesla Model 3", pub.carModel());
        assertEquals(2, pub.totalCharges());
        assertEquals(2, pub.recentCharges().size());
    }

    @Test
    void createShare_carriesReferralCodeOfOwner() {
        // Der Link ist die einzige Stelle, an der der Teilende selbst etwas davon hat:
        // registriert sich der Empfaenger, zaehlt das als Empfehlung.
        User user = createAndSaveUser("carshare-ref-" + System.nanoTime() + "@test.com");
        assertNotNull(user.getReferralCode(), "Testnutzer braucht einen Referral-Code");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        ShareResponse share = shareService.createShare(car.getId(), user);

        assertTrue(share.url().contains("/fahrzeug/" + share.token() + "?ref=" + user.getReferralCode()), share.url());
    }

    @Test
    void getPublicCar_linksToModelPage() {
        User user = createAndSaveUser("carshare-model-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        PublicCarResponse pub = shareService.getPublicCar(shareService.createShare(car.getId(), user).token()).orElseThrow();

        assertEquals("/modelle/Tesla/Model_3", pub.modelPagePath());
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
    void getPublicCar_includesPeerComparison_whenPeersExist() {
        // Der Vergleich zum Modell-Schnitt ist die eine Zahl, die die Seite fuer
        // einen Aussenstehenden einordnet. Anzahl der Fahrer ja, nie wer.
        VehicleSpecification spec = vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                "Tesla", "Model 3", new BigDecimal("75.0"), null, new BigDecimal("490"), new BigDecimal("15.4"),
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP,
                "carshare-peer-" + System.nanoTime()));
        User owner = createAndSaveUser("carshare-peer-owner-" + System.nanoTime() + "@test.com");
        Car ownerCar = carRepository.save(createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3)
                .toBuilder().vehicleSpecificationId(spec.getId()).build());
        User peer = createAndSaveUser("carshare-peer-" + System.nanoTime() + "@test.com");
        Car peerCar = carRepository.save(createAndSaveCar(peer.getId(), CarBrand.CarModel.MODEL_3)
                .toBuilder().vehicleSpecificationId(spec.getId()).build());
        saveLog(ownerCar.getId(), LocalDateTime.now().minusDays(2), 10_000);
        saveLog(ownerCar.getId(), LocalDateTime.now().minusDays(1), 10_250);
        saveLog(peerCar.getId(), LocalDateTime.now().minusDays(2), 20_000);
        saveLog(peerCar.getId(), LocalDateTime.now().minusDays(1), 20_200);

        PublicCarResponse pub = shareService.getPublicCar(shareService.createShare(ownerCar.getId(), owner).token()).orElseThrow();

        assertNotNull(pub.peerComparison());
        assertNotNull(pub.peerComparison().peerAvgConsumptionKwhPer100km());
        assertEquals(1, pub.peerComparison().peerUsers());
        assertFalse(pub.toString().contains(peer.getId().toString()), "Peer-Identitaet darf nicht exponiert werden");
    }

    @Test
    void getPublicCar_noPeers_hasNoComparison() {
        User user = createAndSaveUser("carshare-nopeer-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        PublicCarResponse pub = shareService.getPublicCar(shareService.createShare(car.getId(), user).token()).orElseThrow();

        assertNull(pub.peerComparison());
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
