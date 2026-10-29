package com.evmonitor.application.ingest;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connector-Ladungen mit unbekanntem Ladeort: das Gateway stuft sie vor der Preisübernahme ein, damit
 * Ortspreis oder Heimtarif greifen (Testmatrix B in ladeort-erkennung/plan.md).
 */
class IngestGatewayChargingPlaceTest extends AbstractIntegrationTest {

    private static final String HERE = "u33df5x";
    private static final String FIELD = "u33df5";

    @Autowired private IngestGateway gateway;
    @Autowired private JpaUserChargingProviderRepository cardRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        userId = createAndSaveUser("ingest-place-" + System.nanoTime() + "@example.com").getId();
        carId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void b1_acAtPricedDcCell_isPublicButInheritsNoDcPrice() {
        charged(HERE, true, ChargingType.DC, new BigDecimal("17.61"));

        EvLog log = ingestAc(HERE);

        assertThat(log.getPublicCharging()).isTrue();
        assertThat(log.getGeohash()).isEqualTo(HERE);
        assertThat(log.getCostEur()).isNull();
    }

    @Test
    void b2_acAtPricedAcCell_inheritsThePrice() {
        charged(HERE, true, ChargingType.AC, new BigDecimal("10.00"));

        EvLog log = ingestAc(HERE);

        assertThat(log.getPublicCharging()).isTrue();
        // 10,00 EUR / 20 kWh = 0,50 EUR/kWh, neue Ladung 20 kWh
        assertThat(log.getCostEur()).isEqualByComparingTo("10.00");
    }

    @Test
    void b3_acInPrivateField_isPrivateWithSixCharsAndHomeTariff() {
        charged(FIELD, false, ChargingType.AC, null);
        saveHomeCard(new BigDecimal("0.30"));

        EvLog log = ingestAc(HERE);

        assertThat(log.getPublicCharging()).isFalse();
        assertThat(log.getGeohash()).isEqualTo(FIELD);
        assertThat(log.getCostEur()).isEqualByComparingTo("6.00");
    }

    @Test
    void b5_homeCardWithoutPrivateChargeInField_staysPublicWithoutHomeTariff() {
        saveHomeCard(new BigDecimal("0.30"));

        EvLog log = ingestAc(HERE);

        assertThat(log.getPublicCharging()).isTrue();
        assertThat(log.getGeohash()).isEqualTo(HERE);
        assertThat(log.getCostEur()).isNull();
    }

    /** Alter Connector während des Deploys: 6 Stellen, unbekannt - im privaten Feld greift der Heimtarif. */
    @Test
    void sixCharsFromOldConnector_inPrivateField_getsHomeTariff() {
        charged(FIELD, false, ChargingType.AC, null);
        saveHomeCard(new BigDecimal("0.30"));

        EvLog log = ingestAc(FIELD);

        assertThat(log.getPublicCharging()).isFalse();
        assertThat(log.getCostEur()).isEqualByComparingTo("6.00");
    }

    private EvLog ingestAc(String geohash) {
        IngestResult result = gateway.ingestCharging(new IngestCommand(userId, carId, DataSource.TESLA_LIVE,
                IngestDoor.CONNECTOR_PUSH, List.of(ChargingEntry.builder()
                        .loggedAt(LocalDateTime.now().minusHours(1))
                        .kwhCharged(new BigDecimal("20.0"))
                        .chargingType(ChargingType.AC)
                        .geohash(geohash)
                        .build())));
        return evLogRepository.findById(result.created().get(0).getId()).orElseThrow();
    }

    private void charged(String geohash, Boolean publicCharging, ChargingType type, BigDecimal cost) {
        LocalDateTime now = LocalDateTime.now();
        evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("20.0"))
                .costEur(cost)
                .geohash(geohash)
                .publicCharging(publicCharging)
                .chargingType(type)
                .loggedAt(now.minusDays(2))
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void saveHomeCard(BigDecimal ac) {
        UserChargingProviderEntity card = new UserChargingProviderEntity();
        card.setUserId(userId);
        card.setProviderName("Haushaltsstrom");
        card.setAcPricePerKwh(ac);
        card.setSessionFeeEur(BigDecimal.ZERO);
        card.setMonthlyFeeEur(BigDecimal.ZERO);
        card.setActiveFrom(LocalDate.now().minusYears(1));
        card.setPrivateCard(true);
        cardRepository.save(card);
    }
}
