package com.evmonitor.application;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rueckwirkendes Bepreisen aller Heimladungen mit dem als privat markierten Tarif.
 *
 * Motivation: XPeng liefert per EU-Data-Act-Export weder Ort noch Preis. Ein solcher Nutzer
 * hat nach dem Import dutzende Ladungen ohne Kosten, die der ortsbasierte Nachtrag nie
 * erreicht - es gibt keinen Geohash, an dem er ansetzen koennte.
 */
class EvLogServiceApplyHomeTariffTest extends AbstractIntegrationTest {

    @Autowired private EvLogService evLogService;
    @Autowired private JpaUserChargingProviderRepository cardRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("home-tariff-" + System.nanoTime() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "HT-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void pricesEveryPricelessHomeChargeWithTheHomeCard() {
        UUID card = saveHomeCard(new BigDecimal("0.2500"));
        EvLog a = saveLog(new BigDecimal("10.0"), null, false, ChargingType.AC);
        EvLog b = saveLog(new BigDecimal("4.0"), null, false, ChargingType.AC);

        EvLogService.TariffApplied applied = evLogService.applyHomeTariff(userId);

        assertEquals(2, applied.priced());
        assertEquals(0, new BigDecimal("2.50").compareTo(reload(a).getCostEur()));
        assertEquals(0, new BigDecimal("1.00").compareTo(reload(b).getCostEur()));
        assertEquals(card, reload(a).getChargingProviderId());
        assertEquals(0, new BigDecimal("0.2500").compareTo(reload(a).getPricePerKwh()));
    }

    @Test
    void neverTouchesPublicCharges() {
        saveHomeCard(new BigDecimal("0.2500"));
        EvLog pub = saveLog(new BigDecimal("10.0"), null, true, ChargingType.AC);

        assertEquals(0, evLogService.applyHomeTariff(userId).priced());
        assertNull(reload(pub).getCostEur());
    }

    @Test
    void neverOverwritesAnExistingCost() {
        saveHomeCard(new BigDecimal("0.2500"));
        EvLog priced = saveLog(new BigDecimal("10.0"), new BigDecimal("9.99"), false, ChargingType.AC);

        assertEquals(0, evLogService.applyHomeTariff(userId).priced());
        assertEquals(0, new BigDecimal("9.99").compareTo(reload(priced).getCostEur()));
    }

    @Test
    void doesNothingWithoutAHomeCard() {
        saveLog(new BigDecimal("10.0"), null, false, ChargingType.AC);
        assertEquals(0, evLogService.applyHomeTariff(userId).priced());
    }

    @Test
    void doesNothingWhenTwoHomeCardsAreActive() {
        saveHomeCard(new BigDecimal("0.2500"));
        saveHomeCard(new BigDecimal("0.3200"));
        EvLog log = saveLog(new BigDecimal("10.0"), null, false, ChargingType.AC);

        assertEquals(0, evLogService.applyHomeTariff(userId).priced());
        assertNull(reload(log).getCostEur());
    }

    @Test
    void skipsChargesFromBeforeTheHomeTariffWasActive() {
        UserChargingProviderEntity card = homeCard(new BigDecimal("0.2500"));
        card.setActiveFrom(LocalDate.now().minusDays(2));
        cardRepository.save(card);
        EvLog old = evLogRepository.save(EvLog.createNew(carId, new BigDecimal("10.0"), null, 45, null,
                10_000, null, null, LocalDateTime.now().minusDays(20), ChargingType.AC, null, null, false, null));

        assertEquals(0, evLogService.applyHomeTariff(userId).priced());
        assertNull(reload(old).getCostEur());
    }

    @Test
    void aForeignUsersChargesAreNeverPriced() {
        saveHomeCard(new BigDecimal("0.2500"));
        saveLog(new BigDecimal("10.0"), null, false, ChargingType.AC);
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        Car theirCar = Car.createNew(stranger.getId(), CarBrand.CarModel.MODEL_3, 2023, "HT-2",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(theirCar);
        EvLog theirs = evLogRepository.save(EvLog.createNew(theirCar.getId(), new BigDecimal("10.0"), null,
                45, null, 10_000, null, null, LocalDateTime.now().minusDays(1), ChargingType.AC, null, null, false, null));

        assertEquals(1, evLogService.applyHomeTariff(userId).priced(), "only the owner's charge");
        assertNull(reload(theirs).getCostEur());
    }

    // ---- Helpers ----

    private UserChargingProviderEntity homeCard(BigDecimal acPrice) {
        UserChargingProviderEntity card = new UserChargingProviderEntity();
        card.setUserId(userId);
        card.setProviderName("Zuhause");
        card.setAcPricePerKwh(acPrice);
        card.setSessionFeeEur(BigDecimal.ZERO);
        card.setMonthlyFeeEur(BigDecimal.ZERO);
        card.setActiveFrom(LocalDate.now().minusYears(1));
        card.setPrivateCard(true);
        return card;
    }

    private UUID saveHomeCard(BigDecimal acPrice) {
        return cardRepository.save(homeCard(acPrice)).getId();
    }

    private EvLog saveLog(BigDecimal kwh, BigDecimal cost, boolean isPublic, ChargingType type) {
        return evLogRepository.save(EvLog.createNew(carId, kwh, cost, 45, null, 10_000, null, null,
                LocalDateTime.now().minusDays(1), type, null, null, isPublic, null));
    }

    private EvLog reload(EvLog log) {
        return evLogRepository.findById(log.getId()).orElseThrow();
    }
}
