package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.TodayChargeRow;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Heute"-Einträge im Ticker zeigen einzelne Ladungen anderen Nutzern. Hier wird jede
 * Ausschlussregel einzeln geprüft. Eigenes Zeitfenster in der Zukunft, damit "jetzt"-Logs
 * anderer Tests nicht stören.
 */
class TickerTodayChargesQueryTest extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardQueryRepository queries;

    /** Jeder Test bekommt seinen eigenen Tag, die H2-Daten überleben den einzelnen Test. */
    private static final AtomicInteger DAY = new AtomicInteger();

    private LocalDateTime dayStart;
    private LocalDateTime dayEnd;

    @BeforeEach
    void setUp() {
        dayStart = LocalDateTime.of(2032, 3, 10, 0, 0).plusDays(3L * DAY.incrementAndGet());
        dayEnd = dayStart.plusDays(1);
    }

    @Test
    void plausiblePublicCharge_isListed_withoutProviderOfSingleUser() {
        Car car = carOf(newUser());
        publicCharge(car, "42.0", "16.38", "Einzel-" + UUID.randomUUID(), dayStart.plusHours(9));

        List<TodayChargeRow> rows = queries.getTodayPublicCharges(dayStart, dayEnd);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).kwh()).isEqualByComparingTo("42.0");
        assertThat(rows.get(0).costEur()).isEqualByComparingTo("16.38");
        assertThat(rows.get(0).provider()).as("Freitext eines einzelnen Nutzers").isNull();
    }

    @Test
    void providerUsedByThreeUsers_isNamed() {
        String provider = "Anbieter-" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 3; i++) {
            publicCharge(carOf(newUser()), "30.0", "15.00", provider, dayStart.plusHours(8 + i));
        }

        List<TodayChargeRow> rows = queries.getTodayPublicCharges(dayStart, dayEnd);

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> assertThat(r.provider()).isEqualTo(provider));
        assertThat(rows.get(0).costEur()).as("neueste zuerst").isEqualByComparingTo("15.00");
    }

    @Test
    void optedOutUser_isExcluded() {
        User user = newUser();
        userRepository.setTickerShareCharges(user.getId(), false);
        publicCharge(carOf(user), "40.0", "16.00", null, dayStart.plusHours(10));

        assertThat(queries.getTodayPublicCharges(dayStart, dayEnd)).isEmpty();
    }

    @Test
    void homeAndUnknownLocation_areExcluded() {
        Car car = carOf(newUser());
        save(EvLog.createNew(car.getId(), new BigDecimal("40.0"), new BigDecimal("12.00"), 60, null, null,
                null, null, dayStart.plusHours(10), null, null, null, false, null));
        save(EvLog.createNew(car.getId(), new BigDecimal("40.0"), new BigDecimal("12.00"), 60, null, null,
                null, null, dayStart.plusHours(11), null, null, null, null, null));

        assertThat(queries.getTodayPublicCharges(dayStart, dayEnd)).isEmpty();
    }

    @Test
    void trollValues_areExcluded() {
        Car car = carOf(newUser());
        publicCharge(car, "0.5", "0.20", null, dayStart.plusHours(8));    // unter 1 kWh
        publicCharge(car, "151.0", "60.00", null, dayStart.plusHours(9)); // über 150 kWh
        publicCharge(car, "40.0", "2.00", null, dayStart.plusHours(10));  // 0,05 €/kWh
        publicCharge(car, "40.0", "80.00", null, dayStart.plusHours(11)); // 2,00 €/kWh
        publicCharge(car, "40.0", null, null, dayStart.plusHours(12));    // ohne Preis

        assertThat(queries.getTodayPublicCharges(dayStart, dayEnd)).isEmpty();
    }

    @Test
    void seedUser_softDeletedCar_andOtherDays_areExcluded() {
        User seed = userRepository.save(newUser().toBuilder().seedData(true).build());
        publicCharge(carOf(seed), "40.0", "16.00", null, dayStart.plusHours(10));

        Car deleted = carOf(newUser());
        publicCharge(deleted, "40.0", "16.00", null, dayStart.plusHours(10));
        carRepository.save(deleted.softDelete());

        Car other = carOf(newUser());
        publicCharge(other, "40.0", "16.00", null, dayStart.minusMinutes(1));
        publicCharge(other, "40.0", "16.00", null, dayEnd);

        assertThat(queries.getTodayPublicCharges(dayStart, dayEnd)).isEmpty();
    }

    // ---- helpers ----

    private User newUser() {
        return createAndSaveUser("today-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
    }

    private Car carOf(User user) {
        return createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    private UUID publicCharge(Car car, String kwh, String cost, String cpoName, LocalDateTime at) {
        return save(EvLog.createNew(car.getId(), new BigDecimal(kwh), cost == null ? null : new BigDecimal(cost),
                30, null, null, null, null, at, ChargingType.DC, null, null, true, cpoName));
    }

    private UUID save(EvLog log) {
        return evLogRepository.save(log).getId();
    }
}
