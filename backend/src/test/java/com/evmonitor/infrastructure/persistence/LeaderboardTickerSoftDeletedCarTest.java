package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Community-Ticker-Aggregate liefen ohne Car-Join und zählten Logs von Autos im
 * 7-Tage-Papierkorb mit. Ein soft-gelöschtes Auto darf in keiner Zahl mehr auftauchen.
 * Läuft auf H2 wie die übrigen Integrationstests; der Testcontainers-Test des Repositories
 * ist lokal deaktiviert.
 */
class LeaderboardTickerSoftDeletedCarTest extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardQueryRepository queries;

    private LocalDateTime start;
    private LocalDateTime end;
    private Car car;

    @BeforeEach
    void setUp() {
        // Weites Fenster, damit parallel laufende Tests mit "jetzt"-Logs nicht stören: wir
        // vergleichen nur Differenzen vor und nach dem Soft-Delete.
        start = LocalDateTime.of(2031, 1, 1, 0, 0);
        end = start.plusYears(1);
        User user = createAndSaveUser("ticker-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("40.0"), new BigDecimal("12.00"), 90,
                "u33d1", null, null, null, start.plusMonths(6), DataSource.USER_LOGGED, ChargingType.AC, null));
    }

    @Test
    void ticker_ignoresLogsOfSoftDeletedCar() {
        BigDecimal kwhBefore = queries.getTotalKwhThisMonth(start, end);
        com.evmonitor.application.ChargeCountStats countBefore = queries.getChargeCountStats(start, end);
        long minutesBefore = queries.getTotalChargeDurationMinutes(start, end);
        BigDecimal costBefore = queries.getTotalCostEur(start, end);

        carRepository.save(car.softDelete());

        assertThat(queries.getTotalKwhThisMonth(start, end)).isEqualByComparingTo(kwhBefore.subtract(new BigDecimal("40.0")));
        assertThat(queries.getChargeCountStats(start, end).total()).isEqualTo(countBefore.total() - 1);
        assertThat(queries.getTotalChargeDurationMinutes(start, end)).isEqualTo(minutesBefore - 90);
        assertThat(queries.getTotalCostEur(start, end)).isEqualByComparingTo(costBefore.subtract(new BigDecimal("12.00")));
    }
}
