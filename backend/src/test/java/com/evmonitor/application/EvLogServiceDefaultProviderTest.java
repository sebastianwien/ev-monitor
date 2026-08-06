package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
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
 * Attribution of a charge to the user's charging card when the log carries none.
 *
 * The location the charge happened at is the only evidence we accept: the card the user last
 * picked at this exact geohash. Without a usable location there is no evidence at all, so the
 * log stays unattributed and unpriced - even for a user holding a single card. Assuming that
 * card invents a tariff for a charge point it may never have paid for; a Tesla Supercharger
 * priced with a supermarket card is the concrete failure this rule prevents.
 *
 * Nothing is lost permanently: {@code updateGeohash} re-runs attribution as soon as a connector
 * backfills the location.
 */
class EvLogServiceDefaultProviderTest extends AbstractIntegrationTest {

    private static final double CHARGE_POINT_LAT = 52.5163;
    private static final double CHARGE_POINT_LON = 13.3777;
    private static final String OTHER_LOCATION = "u1m9r98";

    @Autowired
    private EvLogService evLogService;

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("default-provider-" + System.nanoTime() + "@example.com");
        userId = user.getId();

        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "DEF-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    // ---- Manual entry (logCharging) ----

    @Test
    void publicChargeWithoutALocationStaysUnattributed() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);

        EvLog log = manualLog(true, null);

        assertNull(log.getChargingProviderId(),
                "A single card is not evidence that it paid for this particular charge point");
    }

    @Test
    void homeChargeNeverInheritsThePublicCard() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);

        EvLog log = manualLog(false, null);

        assertNull(log.getChargingProviderId(), "A home charge is not paid with a public charging card");
    }

    @Test
    void twoActiveCardsStayUnattributed() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        saveProvider("IONITY Passport", new BigDecimal("0.4900"), new BigDecimal("0.3900"), null);

        EvLog log = manualLog(true, null);

        assertNull(log.getChargingProviderId(), "Ambiguous - the user must pick, we must not guess");
    }

    @Test
    void anExplicitlyChosenCardAlwaysWins() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        UUID ionity = saveProvider("IONITY Passport", new BigDecimal("0.4900"), new BigDecimal("0.3900"), null);

        EvLog log = manualLog(true, ionity);

        assertEquals(ionity, log.getChargingProviderId());
    }

    @Test
    void aForeignUsersCardAtTheSameLocationIsNeverUsed() {
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        UUID strangersCard = saveProviderFor(stranger.getId(), "EnBW mobility+",
                new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        Car strangersCar = Car.createNew(stranger.getId(), CarBrand.CarModel.MODEL_3, 2023, "STR-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(strangersCar);
        // The stranger charged at this very charge point and paid with their card. That says
        // nothing about who paid for our user's charge at the same place.
        evLogRepository.save(EvLog.createNew(strangersCar.getId(), new BigDecimal("40.0"),
                new BigDecimal("20.00"), 30, geohashOfChargePoint(), 9_000, new BigDecimal("150.0"),
                new BigDecimal("80.0"), LocalDateTime.now().minusDays(5), ChargingType.DC, null, null,
                true, null)
                .toBuilder().chargingProviderId(strangersCard).build());

        EvLog log = manualLogAtChargePoint();

        assertNull(log.getChargingProviderId());
    }

    @Test
    void theCardUsedAtThisLocationBeforeIsTheAnswer() {
        UUID enbw = saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        UUID ionity = saveProvider("IONITY Passport", new BigDecimal("0.4900"), new BigDecimal("0.3900"), null);
        // A previous charge here was explicitly paid with IONITY. Two active cards means the
        // default-card rule gives up - but the location history still knows the answer.
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("20.00"), 30,
                geohashOfChargePoint(), 9_000, new BigDecimal("150.0"), new BigDecimal("80.0"),
                LocalDateTime.now().minusDays(5), ChargingType.DC, null, null, true, null)
                .toBuilder().chargingProviderId(ionity).build());

        EvLog log = manualLogAtChargePoint();

        assertEquals(ionity, log.getChargingProviderId());
        assertNotEquals(enbw, log.getChargingProviderId());
    }

    // ---- Connector / import (createInternalLog) ----

    @Test
    void importedChargeAtAKnownLocationInheritsThePriceAndTheCard() {
        UUID enbw = saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        chargedHereBeforeWith(enbw);

        EvLog log = internalLog(new BigDecimal("50.0"), null, true, geohashOfChargePoint());

        assertEquals(enbw, log.getChargingProviderId());
        // The anchor charge cost 20.00 for 40 kWh = 0.50/kWh, so 50 kWh = 25.00. What the user
        // actually paid there beats the card's list price of 0.59 DC.
        assertEquals(0, new BigDecimal("25.00").compareTo(log.getCostEur()));
    }

    @Test
    void importedChargeInheritsThePrice_evenWhenTheEarlierChargeHasNoCard() {
        // Home charging: the user types a price and owns no card for it. Repeating that price is
        // exactly what they expect - the previous behaviour left every such charge unpriced.
        chargedHereBeforeWith(null);

        EvLog log = internalLog(new BigDecimal("10.0"), null, false, geohashOfChargePoint());

        assertNull(log.getChargingProviderId());
        // 20.00 for 40 kWh = 0.50/kWh -> 10 kWh = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(log.getCostEur()));
    }

    @Test
    void importedChargeThatAlreadyHasACostKeepsItButIsStillAttributed() {
        UUID enbw = saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        chargedHereBeforeWith(enbw);

        // Tesla Supercharger reports its own billed cost - that number always wins over our tariff.
        EvLog log = internalLog(new BigDecimal("50.0"), new BigDecimal("18.40"), true, geohashOfChargePoint());

        assertEquals(enbw, log.getChargingProviderId());
        assertEquals(0, new BigDecimal("18.40").compareTo(log.getCostEur()));
    }

    @Test
    void importedPublicChargeWithoutGeohashIsNeitherAttributedNorPriced() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);

        EvLog log = internalLog(new BigDecimal("50.0"), null, true, null);

        assertNull(log.getChargingProviderId(),
                "A Tesla Supercharger session must not inherit the user's supermarket card");
        assertNull(log.getCostEur());
    }

    @Test
    void aTooCoarseGeohashIsTreatedAsNoLocationAtAll() {
        UUID enbw = saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        chargedHereBeforeWith(enbw);

        // Connectors are free to send whatever they have. 5 chars span ~2.4km - too coarse to
        // identify a charge point, so the location lookup is skipped rather than attempted.
        EvLog log = internalLog(new BigDecimal("50.0"), null, true, "u2edq");

        assertNull(log.getChargingProviderId());
        assertNull(log.getCostEur());
    }

    @Test
    void importedHomeChargeIsNotAttributed() {
        saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);

        EvLog log = internalLog(new BigDecimal("11.0"), null, false, OTHER_LOCATION);

        assertNull(log.getChargingProviderId());
        assertNull(log.getCostEur(), "Wallbox charge at home must not be priced with a public tariff");
    }

    @Test
    void aBackfilledGeohashAttributesAndPricesTheLogAfterwards() {
        UUID enbw = saveProvider("EnBW mobility+", new BigDecimal("0.3900"), new BigDecimal("0.5900"), null);
        chargedHereBeforeWith(enbw);
        EvLog log = internalLog(new BigDecimal("50.0"), null, true, null);
        assertNull(log.getChargingProviderId());

        // The connector learns the location later (Tesla streams it only on change) and backfills it.
        evLogService.updateGeohash(carId, userId, log.getLoggedAt(), geohashOfChargePoint());

        EvLog enriched = reload(log.getId());
        assertEquals(enbw, enriched.getChargingProviderId());
        assertEquals(0, new BigDecimal("25.00").compareTo(enriched.getCostEur()));
    }

    // ---- Helpers ----

    private UUID saveProvider(String name, BigDecimal ac, BigDecimal dc, LocalDate activeUntil) {
        return saveProviderFor(userId, name, ac, dc, activeUntil);
    }

    private UUID saveProviderFor(UUID owner, String name, BigDecimal ac, BigDecimal dc, LocalDate activeUntil) {
        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(owner);
        p.setProviderName(name);
        p.setAcPricePerKwh(ac);
        p.setDcPricePerKwh(dc);
        p.setSessionFeeEur(BigDecimal.ZERO);
        p.setMonthlyFeeEur(BigDecimal.ZERO);
        p.setActiveFrom(LocalDate.now().minusYears(1));
        p.setActiveUntil(activeUntil);
        return chargingProviderRepository.save(p).getId();
    }

    /** Manual log without coordinates - the only signal is isPublicCharging. */
    private EvLog manualLog(boolean isPublic, UUID chargingProviderId) {
        EvLogRequest request = new EvLogRequest(carId, new BigDecimal("50.0"), new BigDecimal("25.00"),
                45, null, null, 10_000, null, new BigDecimal("80.0"), new BigDecimal("20.0"), null,
                LocalDateTime.now().minusHours(1), false, ChargingType.DC, null, null,
                isPublic, null, null, null, chargingProviderId);
        return reload(evLogService.logCharging(userId, request).log().id());
    }

    /** An earlier charge at {@link #geohashOfChargePoint()}, optionally paid with a card. */
    private void chargedHereBeforeWith(UUID providerId) {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), new BigDecimal("20.00"), 30,
                geohashOfChargePoint(), 9_000, new BigDecimal("150.0"), new BigDecimal("80.0"),
                LocalDateTime.now().minusDays(5), ChargingType.DC, null, null, true, null)
                .toBuilder().chargingProviderId(providerId).build());
    }

    /** The geohash the service derives for a public charge at {@link #CHARGE_POINT_LAT}/{@code LON}. */
    private String geohashOfChargePoint() {
        return GeoHash.withCharacterPrecision(CHARGE_POINT_LAT, CHARGE_POINT_LON, 7).toBase32();
    }

    /** Manual public log at the coordinates of {@link #geohashOfChargePoint()}. */
    private EvLog manualLogAtChargePoint() {
        EvLogRequest request = new EvLogRequest(carId, new BigDecimal("50.0"), new BigDecimal("25.00"),
                45, CHARGE_POINT_LAT, CHARGE_POINT_LON, 10_100, null, new BigDecimal("80.0"),
                new BigDecimal("20.0"), null, LocalDateTime.now(), false, ChargingType.DC, null, null,
                true, null, null, null, null);
        return reload(evLogService.logCharging(userId, request).log().id());
    }

    private EvLog internalLog(BigDecimal kwh, BigDecimal cost, boolean isPublic, String geohash) {
        InternalEvLogRequest request = new InternalEvLogRequest(carId, userId, kwh, 40,
                LocalDateTime.now().minusHours(2), geohash, null, null, "TESLA_LIVE", cost,
                "DC", false, 12_000, new BigDecimal("20.0"), new BigDecimal("80.0"), null, null,
                isPublic, null, null, null, null, null);
        return reload(evLogService.createInternalLog(request).id());
    }

    private EvLog reload(UUID id) {
        return evLogRepository.findById(id).orElseThrow();
    }
}
