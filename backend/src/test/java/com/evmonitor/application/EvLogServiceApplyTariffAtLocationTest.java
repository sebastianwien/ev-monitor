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
 * Retroactively applying a charging-card tariff to all price-less logs at one location.
 *
 * Motivating case: users who imported their history (Tessie) have logs with geohash but
 * no cost - the existing geohash price-suggestion cannot help them because it anchors on
 * a *previous* log that already carries a chargingProviderId, and an import produces none.
 */
class EvLogServiceApplyTariffAtLocationTest extends AbstractIntegrationTest {

    private static final String LOCATION = "u1mc1v8";
    private static final String OTHER_LOCATION = "u1m9r98";

    @Autowired
    private EvLogService evLogService;

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    private UUID userId;
    private UUID carId;
    private UUID providerId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("tariff-" + System.nanoTime() + "@example.com");
        userId = user.getId();

        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "TAR-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();

        providerId = saveProvider(userId, new BigDecimal("0.3900"), new BigDecimal("0.5900"));
    }

    private UUID saveProvider(UUID owner, BigDecimal acPrice, BigDecimal dcPrice) {
        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(owner);
        p.setProviderName("EnBW mobility+");
        p.setAcPricePerKwh(acPrice);
        p.setDcPricePerKwh(dcPrice);
        p.setSessionFeeEur(BigDecimal.ZERO);
        p.setMonthlyFeeEur(new BigDecimal("11.99"));
        p.setActiveFrom(LocalDate.now().minusYears(2));
        return chargingProviderRepository.save(p).getId();
    }

    private EvLog saveLog(String geohash, ChargingType type, BigDecimal kwh, BigDecimal cost) {
        return evLogRepository.save(EvLog.createNew(carId, kwh, cost, 45, geohash, 10_000,
                new BigDecimal("150.0"), new BigDecimal("80.0"), LocalDateTime.now().minusDays(3),
                type, null, null, true, null));
    }

    @Test
    void appliesDcAndAcPriceToPricelessLogsAtLocation() {
        EvLog dc = saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);
        EvLog ac = saveLog(LOCATION, ChargingType.AC, new BigDecimal("10.0"), null);

        int updated = evLogService.applyTariffAtLocation(userId, LOCATION, providerId);

        assertEquals(2, updated);
        // 50 kWh * 0.59 = 29.50
        assertEquals(0, new BigDecimal("29.50").compareTo(reload(dc).getCostEur()));
        // 10 kWh * 0.39 = 3.90
        assertEquals(0, new BigDecimal("3.90").compareTo(reload(ac).getCostEur()));
        assertEquals(providerId, reload(dc).getChargingProviderId());
    }

    @Test
    void neverOverwritesACostTheUserAlreadySet() {
        EvLog priced = saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), new BigDecimal("12.00"));

        int updated = evLogService.applyTariffAtLocation(userId, LOCATION, providerId);

        assertEquals(0, updated);
        assertEquals(0, new BigDecimal("12.00").compareTo(reload(priced).getCostEur()));
    }

    @Test
    void leavesLogsAtOtherLocationsUntouched() {
        EvLog here = saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);
        EvLog elsewhere = saveLog(OTHER_LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);

        int updated = evLogService.applyTariffAtLocation(userId, LOCATION, providerId);

        assertEquals(1, updated);
        assertNotNull(reload(here).getCostEur());
        assertNull(reload(elsewhere).getCostEur(), "Log at a different geohash must not be priced");
    }

    @Test
    void addsSessionFeeOnTopOfEnergyCost() {
        UserChargingProviderEntity p = chargingProviderRepository.findById(providerId).orElseThrow();
        p.setSessionFeeEur(new BigDecimal("0.5000"));
        chargingProviderRepository.save(p);

        EvLog dc = saveLog(LOCATION, ChargingType.DC, new BigDecimal("10.0"), null);

        evLogService.applyTariffAtLocation(userId, LOCATION, providerId);

        // 10 kWh * 0.59 + 0.50 session fee = 6.40
        assertEquals(0, new BigDecimal("6.40").compareTo(reload(dc).getCostEur()));
    }

    @Test
    void rejectsAProviderOwnedByAnotherUser() {
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        UUID foreignProvider = saveProvider(stranger.getId(), new BigDecimal("0.10"), new BigDecimal("0.10"));

        saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);

        assertThrows(IllegalArgumentException.class,
                () -> evLogService.applyTariffAtLocation(userId, LOCATION, foreignProvider));
    }

    @Test
    void skipsLogsWhoseChargingTypeHasNoPriceConfigured() {
        UUID dcOnly = saveProvider(userId, null, new BigDecimal("0.5900"));
        EvLog ac = saveLog(LOCATION, ChargingType.AC, new BigDecimal("10.0"), null);

        int updated = evLogService.applyTariffAtLocation(userId, LOCATION, dcOnly);

        assertEquals(0, updated);
        assertNull(reload(ac).getCostEur(), "No AC price configured - log must stay priceless");
    }

    @Test
    void countsPricelessLogsAtLocationForTheConfirmationPrompt() {
        saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);
        saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);
        saveLog(LOCATION, ChargingType.DC, new BigDecimal("50.0"), new BigDecimal("20.00"));
        saveLog(OTHER_LOCATION, ChargingType.DC, new BigDecimal("50.0"), null);

        assertEquals(2, evLogService.countPricelessLogsAtLocation(userId, LOCATION));
    }

    private EvLog reload(EvLog log) {
        return evLogRepository.findById(log.getId()).orElseThrow();
    }
}
