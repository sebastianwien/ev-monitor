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
 * Watt fuer Datenqualitaet beim Nachtragen: Preis, Ladekarte und CPO bringen je einmal pro
 * Ladung etwas - Aendern eines vorhandenen Werts nichts. Der Sammel-Nachtrag zahlt pro
 * tatsaechlich bepreister Ladung, gedeckelt.
 */
class PriceAmendCoinsIntegrationTest extends AbstractIntegrationTest {

    private static final String HERE = "u1hcpp7";

    @Autowired private EvLogService evLogService;
    @Autowired private JpaUserChargingProviderRepository cardRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("amend-coins-" + System.nanoTime() + "@example.com");
        userId = user.getId();
        carId = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3).getId();
    }

    @Test
    void addingAPriceCardAndCpoEarnsOncePerLog() {
        EvLog log = savePriceless();
        UUID card = saveCard();

        EvLogUpdateResult first = evLogService.updateLogAwardingCoins(log.getId(), userId,
                amend(new BigDecimal("9.80"), null, null));
        assertEquals(3, first.coinsAwarded(), "Preis nachgetragen = 3 Watt");

        EvLogUpdateResult again = evLogService.updateLogAwardingCoins(log.getId(), userId,
                amend(new BigDecimal("10.00"), null, null));
        assertEquals(0, again.coinsAwarded(), "Preis aendern bringt nichts");

        EvLogUpdateResult withCardAndCpo = evLogService.updateLogAwardingCoins(log.getId(), userId,
                amend(null, card, "Aldi"));
        assertEquals(5, withCardAndCpo.coinsAwarded(), "Karte 2 + Ladesaeulen-Betreiber 3");

        assertEquals(8, coinLogRepository.sumCoinsForSourceEntity(log.getId()));
    }

    @Test
    void batchPricingEarnsPerPricedLogAndIsCapped() {
        UUID card = saveCard();
        for (int i = 0; i < 25; i++) savePriceless();

        EvLogService.TariffApplied applied = evLogService.applyTariffAtLocation(userId, HERE, card);

        assertEquals(25, applied.priced());
        assertEquals(20 * 3, applied.coinsAwarded(), "hoechstens 20 Ladungen pro Sammel-Nachtrag zaehlen");
    }

    private EvLogUpdateRequest amend(BigDecimal cost, UUID card, String cpo) {
        return new EvLogUpdateRequest(null, cost, null, null, null, null, null, null, null, null,
                null, null, null, null, null, cpo, null, null, card);
    }

    private EvLog savePriceless() {
        EvLog log = EvLog.createNew(carId, new BigDecimal("20.0"), null, 30, HERE, 10_000, null, null,
                LocalDateTime.now().minusMinutes((long) (Math.random() * 100_000)), ChargingType.AC, null, null, true, null);
        return evLogRepository.save(log);
    }

    private UUID saveCard() {
        UserChargingProviderEntity card = new UserChargingProviderEntity();
        card.setUserId(userId);
        card.setProviderName("EnBW");
        card.setAcPricePerKwh(new BigDecimal("0.4900"));
        card.setMonthlyFeeEur(BigDecimal.ZERO);
        card.setSessionFeeEur(BigDecimal.ZERO);
        card.setActiveFrom(LocalDate.now().minusDays(30));
        return cardRepository.save(card).getId();
    }
}
