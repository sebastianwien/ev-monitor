package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Ein Log an einer Register-Saeule verweist auf den Ladestandort und uebernimmt dessen Zelle:
 * geladen wurde an der Saeule, nicht dort, wo das Handy gerade lag.
 */
class EvLogServiceChargingSiteTest extends AbstractIntegrationTest {

    private static final String CELL = "u33dc0c";
    private static final ChargingSiteRef IONITY = new ChargingSiteRef("IONITY", CELL);

    @Autowired private EvLogService evLogService;
    @Autowired private ChargingSiteService chargingSiteService;
    @MockBean private NearbyCpoService nearbyCpoService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("site-" + System.nanoTime() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "SITE-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(
                new NearbyStation("IONITY", true, 40, 350.0, true, 6, CELL))));
    }

    private EvLogRequest request(ChargingSiteRef site, Double lat, Double lon) {
        return new EvLogRequest(carId, new BigDecimal("40.0"), new BigDecimal("20.00"), null, lat, lon,
                12_000, null, new BigDecimal("80"), null, null, LocalDateTime.now().minusHours(1), null,
                ChargingType.DC, null, null, true, "IONITY", null, null, null, site);
    }

    @Test
    void verknuepftDasLogMitDemStandortUndUebernimmtDessenZelle() {
        // Handyposition liegt in einer anderen Zelle - gespeichert wird die Saeule.
        EvLog saved = reload(evLogService.logCharging(userId, request(IONITY, 52.5215, 13.4090)).log().id());

        assertNotNull(saved.getChargingSiteId());
        assertEquals(CELL, saved.getGeohash());
        assertEquals("IONITY", saved.getCpoName());
    }

    @Test
    void zweiLogsAnDerselbenSaeuleTeilenSichDenStandort() {
        UUID first = reload(evLogService.logCharging(userId, request(IONITY, null, null)).log().id()).getChargingSiteId();
        UUID second = reload(evLogService.logCharging(userId, request(IONITY, null, null)).log().id()).getChargingSiteId();

        assertEquals(first, second);
        assertEquals(1, chargingSiteService.recentlyUsed(userId).size());
        assertEquals(2, chargingSiteService.recentlyUsed(userId).getFirst().usageCount());
    }

    @Test
    void ohneRegistertrefferBleibtDasLogOhneStandortAberMitPosition() {
        EvLog saved = reload(evLogService.logCharging(userId,
                request(new ChargingSiteRef("Phantom", CELL), 52.5215, 13.4090)).log().id());

        assertNull(saved.getChargingSiteId());
        assertEquals(ch.hsr.geohash.GeoHash.withCharacterPrecision(52.5215, 13.4090, 7).toBase32(), saved.getGeohash());
    }

    @Test
    void zuletztGenutzteStandorteGehoerenNurDemEigenenNutzer() {
        evLogService.logCharging(userId, request(IONITY, null, null));
        User other = createAndSaveUser("site-other-" + System.nanoTime() + "@example.com");

        assertEquals(1, chargingSiteService.recentlyUsed(userId).size());
        assertTrue(chargingSiteService.recentlyUsed(other.getId()).isEmpty());
    }

    private EvLog reload(UUID id) {
        return evLogRepository.findById(id).orElseThrow();
    }
}
