package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.MonthChargeSummary;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Monatssumme für den persönlichen Ticker-Eintrag: nur eigene, nicht gelöschte Autos. */
class PersonalTickerQueryTest extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardQueryRepository queries;

    private LocalDateTime start;
    private LocalDateTime end;
    private User user;

    @BeforeEach
    void setUp() {
        start = LocalDateTime.of(2033, 5, 1, 0, 0);
        end = start.plusMonths(1);
        user = createAndSaveUser("personal-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
    }

    @Test
    void userWithoutLogs_hasNoSummary() {
        assertThat(queries.getTopCarMonthSummary(user.getId(), start, end)).isEmpty();
    }

    @Test
    void picksCarWithMostCharges_andCountsHomeShare() {
        Car main = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        Car second = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_Y);
        log(main, "30.0", false, start.plusDays(1));
        log(main, "20.0", false, start.plusDays(2));
        log(main, "10.0", true, start.plusDays(3));
        log(second, "99.0", true, start.plusDays(4));
        log(main, "50.0", false, start.minusDays(1)); // Vormonat

        Optional<MonthChargeSummary> summary = queries.getTopCarMonthSummary(user.getId(), start, end);

        assertThat(summary).isPresent();
        assertThat(summary.get().carId()).isEqualTo(main.getId());
        assertThat(summary.get().charges()).isEqualTo(3);
        assertThat(summary.get().homeCharges()).isEqualTo(2);
        assertThat(summary.get().kwh()).isEqualByComparingTo("60.0");
    }

    @Test
    void foreignCars_neverCount() {
        User other = createAndSaveUser("foreign-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        Car foreign = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        log(foreign, "40.0", false, start.plusDays(1));

        assertThat(queries.getTopCarMonthSummary(user.getId(), start, end)).isEmpty();
    }

    @Test
    void softDeletedCar_doesNotCount() {
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        log(car, "40.0", false, start.plusDays(1));
        carRepository.save(car.softDelete());

        assertThat(queries.getTopCarMonthSummary(user.getId(), start, end)).isEmpty();
    }

    @Test
    void tickerShareCharges_defaultsToTrue_andSurvivesUserSave() {
        assertThat(userRepository.isTickerShareCharges(user.getId())).isTrue();

        userRepository.setTickerShareCharges(user.getId(), false);
        // Ein normaler Save baut die Entity neu auf - darf das Opt-out nicht zurücksetzen.
        userRepository.save(userRepository.findById(user.getId()).orElseThrow());

        assertThat(userRepository.isTickerShareCharges(user.getId())).isFalse();
    }

    private void log(Car car, String kwh, boolean publicCharging, LocalDateTime at) {
        evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal(kwh), null, 60, null, null,
                null, null, at, ChargingType.AC, null, null, publicCharging, null));
    }
}
