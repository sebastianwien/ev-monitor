package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Wochenreport zählt Ladevorgänge nach Ladezeitpunkt (logged_at), nicht nach
 * Eintragungsdatum: ein Import alter Ladungen bläht sonst die Importwoche auf.
 */
class AdminWeeklyEvLogCountTest extends AbstractIntegrationTest {

    @Autowired
    private AdminQueryRepository queries;

    @Test
    void countNewEvLogs_countsByLoggedAtNotCreatedAt() {
        // Fenster weit in der Zukunft: created_at liegt "jetzt", also nie darin.
        LocalDateTime start = LocalDateTime.of(2032, 3, 1, 0, 0);
        LocalDateTime end = start.plusDays(7);
        long before = queries.countNewEvLogs(start, end);

        User user = createAndSaveUser("weekly-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("40.0"), new BigDecimal("12.00"), 90,
                "u33d1", null, null, null, start.plusDays(2), DataSource.USER_LOGGED, ChargingType.AC, null));

        assertThat(queries.countNewEvLogs(start, end)).isEqualTo(before + 1);
    }
}
