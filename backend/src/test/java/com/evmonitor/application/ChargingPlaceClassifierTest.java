package com.evmonitor.application;

import com.evmonitor.application.ChargingPlaceClassifier.ChargingPlace;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unbekannter Ladeort mit Position gilt als öffentlich (7 Stellen), außer der Nutzer hat in derselben
 * 6er-Zelle schon privat geladen: dann ist die neue AC-Ladung privat (6 Stellen). Die Fälle A1-A12
 * der Testmatrix in ladeort-erkennung/plan.md.
 */
class ChargingPlaceClassifierTest extends AbstractIntegrationTest {

    /** 7er-Zelle der neuen Ladung, ihr 6er-Feld ist {@code u33df5}. */
    private static final String HERE = "u33df5x";
    private static final String FIELD = "u33df5";
    /** Andere 7er-Zelle im selben Feld, nicht benachbart zu {@link #HERE}. */
    private static final String ELSEWHERE_IN_FIELD = "u33df50";
    /** Nachbarfeld (anderer 6-Präfix). */
    private static final String NEIGHBOUR_FIELD = "u33df4";

    @Autowired private ChargingPlaceClassifier classifier;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("place-" + System.nanoTime() + "@example.com");
        userId = user.getId();
        carId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void a1_emptyField_isPublicWithSevenChars() {
        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a2_onlyPublicChargesInField_isPublic() {
        charged(carId, ELSEWHERE_IN_FIELD, true);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a3_privateChargeInField_isPrivateWithSixChars() {
        charged(carId, FIELD, false);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(FIELD, false));
    }

    @Test
    void a4a_mixedField_sevenCharHitOnPublicCharge_isPublic() {
        charged(carId, FIELD, false);
        charged(carId, HERE, true);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a4b_mixedField_withoutSevenCharHit_isPrivate() {
        charged(carId, FIELD, false);
        charged(carId, ELSEWHERE_IN_FIELD, true);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(FIELD, false));
    }

    @Test
    void a4c_publicSixCharChargeIsNoSevenCharHit() {
        charged(carId, FIELD, false);
        charged(carId, FIELD, true);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(FIELD, false));
    }

    @Test
    void a5_onlyUnknownLegacyCharges_isPublic() {
        charged(carId, FIELD, null);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a6_softDeletedPrivateCharge_doesNotCount() {
        EvLog deleted = charged(carId, FIELD, false);
        evLogRepository.softDelete(deleted.getId());

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a7_privateChargeOfAnotherUser_doesNotCount() {
        User neighbour = createAndSaveUser("place-neighbour-" + System.nanoTime() + "@example.com");
        charged(createAndSaveCar(neighbour.getId(), CarBrand.CarModel.MODEL_3).getId(), FIELD, false);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a8_dcStaysPublicInPrivateField() {
        charged(carId, FIELD, false);

        assertThat(classifier.classify(userId, HERE, ChargingType.DC, true)).isEqualTo(new ChargingPlace(HERE, true));
        assertThat(classifier.classify(userId, HERE, ChargingType.DC, null)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a9_explicitPrivate_isTruncatedToSixChars() {
        assertThat(classifier.classify(userId, HERE, ChargingType.AC, false)).isEqualTo(new ChargingPlace(FIELD, false));
    }

    @Test
    void a10_explicitPublic_stays() {
        charged(carId, FIELD, false);

        assertThat(classifier.classify(userId, HERE, ChargingType.AC, true)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void a11_withoutPosition_staysUnknown() {
        charged(carId, FIELD, false);

        assertThat(classifier.classify(userId, null, ChargingType.AC, null)).isEqualTo(new ChargingPlace(null, null));
    }

    @Test
    void a12_privateChargeInNeighbourField_doesNotCount() {
        charged(carId, NEIGHBOUR_FIELD, false);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    @Test
    void b4c_sevenCharHitInAdjacentCell_counts() {
        String adjacent = ch.hsr.geohash.GeoHash.fromGeohashString(HERE).getAdjacent()[0].toBase32();
        charged(carId, FIELD, false);
        charged(carId, adjacent, true);

        assertThat(classifyAc(HERE)).isEqualTo(new ChargingPlace(HERE, true));
    }

    /** Alte Connectoren schicken unbekannte Orte mit 6 Stellen: ohne 7er-Zelle lässt sich keine Säule treffen. */
    @Test
    void sixCharsUnknown_isPrivateInPrivateField_elseStaysUnknown() {
        assertThat(classifyAc(FIELD)).isEqualTo(new ChargingPlace(FIELD, null));

        charged(carId, FIELD, false);

        assertThat(classifyAc(FIELD)).isEqualTo(new ChargingPlace(FIELD, false));
    }

    private ChargingPlace classifyAc(String geohash) {
        return classifier.classify(userId, geohash, ChargingType.AC, null);
    }

    private EvLog charged(UUID car, String geohash, Boolean publicCharging) {
        LocalDateTime now = LocalDateTime.now();
        return evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(car)
                .kwhCharged(new BigDecimal("20.0"))
                .geohash(geohash)
                .publicCharging(publicCharging)
                .chargingType(ChargingType.AC)
                .loggedAt(now.minusDays(1))
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
