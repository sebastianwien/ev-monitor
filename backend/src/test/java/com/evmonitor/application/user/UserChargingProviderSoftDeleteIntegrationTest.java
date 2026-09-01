package com.evmonitor.application.user;

import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogService;
import com.evmonitor.domain.*;
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
 * Loeschen einer Ladekarte darf die Vergangenheit nicht umschreiben.
 *
 * ev_log.charging_provider_id ist ON DELETE SET NULL - ein hartes DELETE haette die Karte aus
 * jedem historischen Log des Users gerissen und damit genau die Zuordnung vernichtet, auf der
 * Kostenhistorie und Anbieter-Statistik beruhen.
 */
class UserChargingProviderSoftDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserChargingProviderService service;

    @Autowired
    private EvLogService evLogService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("softdelete-" + System.nanoTime() + "@example.com");
        userId = user.getId();

        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "SD-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void deletingACardKeepsItAttachedToPastCharges() {
        UUID cardId = addCard("EnBW mobility+");
        EvLog charge = saveChargeWithCard(cardId);

        service.delete(userId, cardId);

        EvLog reloaded = evLogRepository.findById(charge.getId()).orElseThrow();
        assertEquals(cardId, reloaded.getChargingProviderId(),
                "Die Ladung von damals wurde mit dieser Karte bezahlt - daran aendert das Loeschen nichts");
    }

    @Test
    void aDeletedCardDisappearsFromThePortfolio() {
        UUID cardId = addCard("EnBW mobility+");

        service.delete(userId, cardId);

        assertTrue(service.getAll(userId).isEmpty());
    }

    @Test
    void aDeletedCardIsNoLongerTheDefaultForNewCharges() {
        UUID cardId = addCard("EnBW mobility+");
        service.delete(userId, cardId);

        EvLogRequest publicCharge = new EvLogRequest(carId, new BigDecimal("30.0"), new BigDecimal("15.00"),
                30, null, null, 12_000, null, new BigDecimal("80.0"), new BigDecimal("20.0"), null,
                LocalDateTime.now().minusMinutes(5), false, ChargingType.DC, null, null,
                true, null, null, null, null);
        UUID createdId = evLogService.logCharging(userId, publicCharge).log().id();

        assertNull(evLogRepository.findById(createdId).orElseThrow().getChargingProviderId(),
                "Eine weggeworfene Karte kann keine neue Ladung bezahlen");
    }

    private UUID addCard(String name) {
        return service.add(userId, new UserChargingProviderRequest(
                name, null, new BigDecimal("0.39"), new BigDecimal("0.59"),
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now().minusMonths(1), null)).id();
    }

    private EvLog saveChargeWithCard(UUID cardId) {
        return evLogRepository.save(EvLog.createNew(carId, new BigDecimal("50.0"), new BigDecimal("29.50"), 45,
                        "u1mc1v8", 10_000, new BigDecimal("150.0"), new BigDecimal("80.0"),
                        LocalDateTime.now().minusDays(10), ChargingType.DC, null, null, true, null)
                .toBuilder().chargingProviderId(cardId).build());
    }
}
