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
 * The one rule for "what does a charge here cost, and who paid for it".
 *
 * One rule: the last priced charge at this location wins, because it is what the user actually
 * paid. A card's configured price is a list price and never derives anything - it applies only
 * where the user explicitly asks for it.
 */
class LocationPricingTest extends AbstractIntegrationTest {

    private static final String HERE = "u1hcpp7";

    @Autowired private LocationPricing locationPricing;
    @Autowired private JpaUserChargingProviderRepository cardRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("location-pricing-" + System.nanoTime() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "LP-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void anchorPriceWinsOverTheCardsListPrice() {
        UUID card = saveCard("EnBW", new BigDecimal("0.3900"), new BigDecimal("0.5900"), BigDecimal.ZERO);
        chargedHere(new BigDecimal("40.0"), new BigDecimal("20.00"), card);

        EvLog priced = locationPricing.enrich(unpricedLog(new BigDecimal("50.0")), userId);

        // 20.00 / 40 kWh = 0.50, not the card's 0.59 DC list price
        assertEquals(0, new BigDecimal("25.00").compareTo(priced.getCostEur()));
        assertEquals(card, priced.getChargingProviderId());
    }

    @Test
    void anAnchorWithoutEnergyIsSkippedInFavourOfTheNextUsableOne() {
        // A cost without a kWh figure cannot yield a price per kWh. Such a log used to be picked
        // as the anchor and then silently discarded, leaving the charge unpriced.
        chargedHere(new BigDecimal("40.0"), new BigDecimal("20.00"), null);
        evLogRepository.save(EvLog.createNew(carId, null, new BigDecimal("9.99"), 30, HERE, 12_000,
                null, null, LocalDateTime.now().minusDays(1), ChargingType.DC, null, null, true, null));

        EvLog priced = locationPricing.enrich(unpricedLog(new BigDecimal("50.0")), userId);

        assertEquals(0, new BigDecimal("25.00").compareTo(priced.getCostEur()));
    }

    @Test
    void aCostTheUserEnteredIsNeverOverwritten() {
        UUID card = saveCard("EnBW", new BigDecimal("0.3900"), new BigDecimal("0.5900"), BigDecimal.ZERO);
        chargedHere(new BigDecimal("40.0"), new BigDecimal("20.00"), card);

        EvLog log = logHere(new BigDecimal("50.0"), new BigDecimal("18.40"));
        EvLog enriched = locationPricing.enrich(log, userId);

        assertEquals(0, new BigDecimal("18.40").compareTo(enriched.getCostEur()));
        assertEquals(card, enriched.getChargingProviderId(), "attribution still happens");
    }

    @Test
    void aCardTheUserPickedIsNeverOverwritten() {
        UUID mine = saveCard("Mine", new BigDecimal("0.2000"), new BigDecimal("0.2000"), BigDecimal.ZERO);
        UUID other = saveCard("Other", new BigDecimal("0.3900"), new BigDecimal("0.5900"), BigDecimal.ZERO);
        chargedHere(new BigDecimal("40.0"), new BigDecimal("20.00"), other);

        EvLog log = unpricedLog(new BigDecimal("50.0")).toBuilder().chargingProviderId(mine).build();

        assertEquals(mine, locationPricing.enrich(log, userId).getChargingProviderId());
    }

    @Test
    void aGeohashShorterThanSixCharsYieldsNothing() {
        assertTrue(locationPricing.tariffAt(userId, "u1hc", null).isEmpty());
        assertTrue(locationPricing.tariffAt(userId, null, null).isEmpty());
    }

    @Test
    void aChargeInheritsOnlyFromAnchorsOfItsOwnChargingType() {
        // Derselbe Standort, zwei Saeulen: die juengste bezahlte Ladung war DC (44 ct),
        // die letzte AC-Ladung (29 ct) liegt weiter zurueck. Eine neue AC-Ladung erbt
        // den AC-Preis - nicht den juengeren DC-Preis (der reale Kaufland-Fall).
        UUID card = saveCard("Kaufland", new BigDecimal("0.2900"), new BigDecimal("0.4400"), BigDecimal.ZERO);
        chargedHere(new BigDecimal("40.0"), new BigDecimal("11.60"), card, ChargingType.AC, 10);
        chargedHere(new BigDecimal("30.0"), new BigDecimal("13.20"), card, ChargingType.DC, 1);

        EvLog log = EvLog.createNew(carId, new BigDecimal("50.0"), null, 30, HERE, 10_000, null, null,
                LocalDateTime.now(), ChargingType.AC, null, null, true, null);
        EvLog priced = locationPricing.enrich(log, userId);

        // 11.60 / 40 kWh = 0.29 ct/kWh Anker, mal 50 kWh
        assertEquals(0, new BigDecimal("14.50").compareTo(priced.getCostEur()));
        assertEquals(card, priced.getChargingProviderId());
    }

    @Test
    void theCardStillAttachesWhenOnlyTheOtherTypeIsPricedHere() {
        // Nur DC wurde hier je bezahlt. Eine AC-Ladung bekommt keinen geerbten Preis -
        // ein DC-Preis waere schlicht falsch - aber die Karte gehoert weiter zum Ort.
        UUID card = saveCard("Kaufland", new BigDecimal("0.2900"), new BigDecimal("0.4400"), BigDecimal.ZERO);
        chargedHere(new BigDecimal("30.0"), new BigDecimal("13.20"), card, ChargingType.DC, 1);

        EvLog log = EvLog.createNew(carId, new BigDecimal("50.0"), null, 30, HERE, 10_000, null, null,
                LocalDateTime.now(), ChargingType.AC, null, null, true, null);
        EvLog enriched = locationPricing.enrich(log, userId);

        assertNull(enriched.getCostEur());
        assertEquals(card, enriched.getChargingProviderId());
    }

    @Test
    void aChargeWithoutATypeKeepsTheTypeBlindAnchor() {
        // Ohne Typ am neuen Log gibt es nichts zu filtern - der juengste bezahlte Log gewinnt.
        chargedHere(new BigDecimal("30.0"), new BigDecimal("13.20"), null, ChargingType.DC, 1);

        EvLog log = EvLog.createNew(carId, new BigDecimal("50.0"), null, 30, HERE, 10_000, null, null,
                LocalDateTime.now(), null, null, null, true, null);
        EvLog priced = locationPricing.enrich(log, userId);

        assertEquals(0, new BigDecimal("22.00").compareTo(priced.getCostEur()));
    }

    @Test
    void aForeignUsersHistoryIsNeverUsed() {
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        Car theirCar = Car.createNew(stranger.getId(), CarBrand.CarModel.MODEL_3, 2023, "LP-2",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(theirCar);
        evLogRepository.save(EvLog.createNew(theirCar.getId(), new BigDecimal("40.0"),
                new BigDecimal("20.00"), 30, HERE, 9_000, null, null,
                LocalDateTime.now().minusDays(1), ChargingType.DC, null, null, true, null));

        assertTrue(locationPricing.tariffAt(userId, HERE, null).isEmpty());
    }

    // ---- Helpers ----

    private UUID saveCard(String name, BigDecimal ac, BigDecimal dc, BigDecimal sessionFee) {
        UserChargingProviderEntity card = new UserChargingProviderEntity();
        card.setUserId(userId);
        card.setProviderName(name);
        card.setAcPricePerKwh(ac);
        card.setDcPricePerKwh(dc);
        card.setSessionFeeEur(sessionFee);
        card.setMonthlyFeeEur(BigDecimal.ZERO);
        card.setActiveFrom(LocalDate.now().minusYears(1));
        return cardRepository.save(card).getId();
    }

    private void chargedHere(BigDecimal kwh, BigDecimal cost, UUID cardId) {
        EvLog log = logHere(kwh, cost);
        evLogRepository.save(cardId != null ? log.toBuilder().chargingProviderId(cardId).build() : log);
    }

    private void chargedHere(BigDecimal kwh, BigDecimal cost, UUID cardId, ChargingType type, int daysAgo) {
        EvLog log = EvLog.createNew(carId, kwh, cost, 30, HERE, 10_000, null, null,
                LocalDateTime.now().minusDays(daysAgo), type, null, null, true, null);
        evLogRepository.save(cardId != null ? log.toBuilder().chargingProviderId(cardId).build() : log);
    }

    private EvLog logHere(BigDecimal kwh, BigDecimal cost) {
        return EvLog.createNew(carId, kwh, cost, 30, HERE, 10_000, null, null,
                LocalDateTime.now().minusDays(2), ChargingType.DC, null, null, true, null);
    }

    private EvLog unpricedLog(BigDecimal kwh) {
        return logHere(kwh, null);
    }
}
