package com.evmonitor.application;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Der Nutzer stuft eine Ladung um, und das wirkt auf künftige Ladungen im selben Feld
 * (Testmatrix C in ladeort-erkennung/plan.md).
 */
class EvLogServiceChargingPlaceTest extends AbstractIntegrationTest {

    private static final String HERE = "u33df5x";
    private static final String FIELD = "u33df5";
    /** Säule im selben Feld wie {@link #HERE}, nicht benachbart. */
    private static final String SITE_CELL = "u33df50";
    private static final ChargingSiteRef SITE = new ChargingSiteRef("EnBW", SITE_CELL);

    @Autowired private EvLogService evLogService;
    @Autowired private IngestGateway gateway;
    @Autowired private JpaUserChargingProviderRepository cardRepository;
    @MockBean private NearbyCpoService nearbyCpoService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        userId = createAndSaveUser("place-update-" + System.nanoTime() + "@example.com").getId();
        carId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3).getId();
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(
                new NearbyStation("EnBW", false, 22, 120.0, false, 2, SITE_CELL))));
    }

    @Test
    void c1_c2_markingPrivate_truncatesAndMakesTheFieldPrivate() {
        UUID id = charged(HERE, true, null, null);

        evLogService.updateLog(id, userId, switchTo(false, null, null));

        assertThat(reload(id).getGeohash()).isEqualTo(FIELD);
        EvLog next = ingestAc(HERE);
        assertThat(next.getPublicCharging()).isFalse();
        assertThat(next.getGeohash()).isEqualTo(FIELD);
    }

    @Test
    void c3_onlyPrivateChargeBackToPublic_keepsSixChars_andFieldIsPublicAgain() {
        UUID id = charged(FIELD, false, null, null);

        evLogService.updateLog(id, userId, switchTo(true, null, null));

        assertThat(reload(id).getGeohash()).isEqualTo(FIELD);
        assertThat(reload(id).getPublicCharging()).isTrue();
        EvLog next = ingestAc(HERE);
        assertThat(next.getPublicCharging()).isTrue();
        assertThat(next.getGeohash()).isEqualTo(HERE);
    }

    @Test
    void c3c_backToPublicAtPickedSite_takesTheSiteCell_andNextAcThereInheritsItsPrice() {
        charged(FIELD, false, null, null);
        UUID id = charged(FIELD, false, null, null);

        evLogService.updateLog(id, userId, switchTo(true, new BigDecimal("10.00"), SITE));

        EvLog switched = reload(id);
        assertThat(switched.getGeohash()).isEqualTo(SITE_CELL);
        assertThat(switched.getChargingSiteId()).isNotNull();
        EvLog next = ingestAc(SITE_CELL);
        assertThat(next.getPublicCharging()).isTrue();
        assertThat(next.getGeohash()).isEqualTo(SITE_CELL);
        assertThat(next.getCostEur()).isEqualByComparingTo("10.00");
    }

    @Test
    void c3e_privateToPublicWithoutCost_dropsHomeTariffCostPriceAndCard() {
        UUID homeCard = saveHomeCard();
        UUID id = charged(FIELD, false, new BigDecimal("6.00"), homeCard);

        evLogService.updateLog(id, userId, switchTo(true, null, null));

        EvLog switched = reload(id);
        assertThat(switched.getCostEur()).isNull();
        assertThat(switched.getPricePerKwh()).isNull();
        assertThat(switched.getChargingProviderId()).isNull();
    }

    @Test
    void c3e_privateToPublicAtPricedSite_takesThePriceFromTheSite() {
        charged(SITE_CELL, true, new BigDecimal("12.00"), null);
        UUID id = charged(FIELD, false, new BigDecimal("6.00"), saveHomeCard());

        evLogService.updateLog(id, userId, switchTo(true, null, SITE));

        assertThat(reload(id).getCostEur()).isEqualByComparingTo("12.00");
    }

    @Test
    void c3f_privateToPublicWithCost_keepsTheRequestCost() {
        UUID id = charged(FIELD, false, new BigDecimal("6.00"), saveHomeCard());

        evLogService.updateLog(id, userId, switchTo(true, new BigDecimal("9.50"), null));

        EvLog switched = reload(id);
        assertThat(switched.getCostEur()).isEqualByComparingTo("9.50");
        // Karte und kWh-Preis gehören zum Heimtarif: blieben sie stehen, bepreiste der alte Preis künftige Ladungen an der Säule
        assertThat(switched.getChargingProviderId()).isNull();
        assertThat(switched.getPricePerKwh()).isNull();
    }

    @Test
    void c3b_pricedPublicSixCharCharge_isNoAnchorForSevenCharCharges() {
        charged(FIELD, true, new BigDecimal("10.00"), null);

        EvLog next = ingestAc(HERE);

        assertThat(next.getPublicCharging()).isTrue();
        assertThat(next.getCostEur()).isNull();
    }

    @Test
    void c5_unknownChargeMarkedPrivate_makesTheFieldPrivate() {
        UUID id = charged(HERE, null, null, null);

        evLogService.updateLog(id, userId, switchTo(false, null, null));

        assertThat(reload(id).getGeohash()).isEqualTo(FIELD);
        assertThat(ingestAc(HERE).getPublicCharging()).isFalse();
    }

    private static EvLogUpdateRequest switchTo(boolean isPublic, BigDecimal cost, ChargingSiteRef site) {
        return new EvLogUpdateRequest(null, cost, null, null, null, null, null, null, null, null, null,
                null, null, null, isPublic, null, null, null, null, site);
    }

    private EvLog ingestAc(String geohash) {
        var result = gateway.ingestCharging(new IngestCommand(userId, carId, DataSource.TESLA_LIVE,
                IngestDoor.CONNECTOR_PUSH, List.of(ChargingEntry.builder()
                        .loggedAt(LocalDateTime.now().minusHours(1))
                        .kwhCharged(new BigDecimal("20.0"))
                        .chargingType(ChargingType.AC)
                        .geohash(geohash)
                        .build())));
        return reload(result.created().get(0).getId());
    }

    /** AC-Ladung mit 20 kWh vor zwei Tagen. */
    private UUID charged(String geohash, Boolean publicCharging, BigDecimal cost, UUID cardId) {
        LocalDateTime now = LocalDateTime.now();
        return evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("20.0"))
                .costEur(cost)
                .pricePerKwh(cost != null ? cost.divide(new BigDecimal("20.0"), 4, java.math.RoundingMode.HALF_UP) : null)
                .chargingProviderId(cardId)
                .geohash(geohash)
                .publicCharging(publicCharging)
                .chargingType(ChargingType.AC)
                .loggedAt(now.minusDays(2))
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();
    }

    private UUID saveHomeCard() {
        UserChargingProviderEntity card = new UserChargingProviderEntity();
        card.setUserId(userId);
        card.setProviderName("Haushaltsstrom");
        card.setAcPricePerKwh(new BigDecimal("0.30"));
        card.setSessionFeeEur(BigDecimal.ZERO);
        card.setMonthlyFeeEur(BigDecimal.ZERO);
        card.setActiveFrom(LocalDate.now().minusYears(1));
        card.setPrivateCard(true);
        return cardRepository.save(card).getId();
    }

    private EvLog reload(UUID id) {
        return evLogRepository.findById(id).orElseThrow();
    }
}
