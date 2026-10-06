package com.evmonitor.application.spritmonitor;

import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Spritmonitor-Re-Import über einem gelöschten Eintrag gegen Postgres mit Flyway: nur hier gilt der
 * partielle Unique-Index {@code uq_ev_log_car_loggedat_datasource} (V190, nur aktive Zeilen).
 * Charakterisiert das heutige Verhalten vor dem IngestGateway (R2g). Übersprungen ohne Docker.
 */
class SpritMonitorTombstonePostgresIT extends AbstractPostgresIT {

    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2024, 1, 15, 0, 0);

    @Autowired SpritMonitorImportService importService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;

    /**
     * Der Index würde eine neue Zeile über dem Tombstone erlauben; übersprungen wird trotzdem, weil
     * die Dedup-Abfrage Tombstones sieht. Das Überspringen ist eine Entscheidung der Dedup, nicht des Index.
     */
    @Test
    void tombstone_blocksReimport_thoughPartialIndexWouldAllowInsert() {
        User user = userRepository.save(TestDataBuilder.createTestUser("sm-pg-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
        when(spritMonitorClient.getFuelings(any(), any(), any())).thenReturn(List.of(new RawFueling(
                new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("40"), 5, new BigDecimal("1000"),
                        new BigDecimal("10.00"), 60, null, null, null, null, null, "AC", null), "{}")));

        importService.importFuelings(user.getId(), "token", 42, 1, car.getId());
        evLogRepository.softDelete(evLogRepository.findAllByCarId(car.getId()).get(0).getId());

        ImportResult reimport = importService.importFuelings(user.getId(), "token", 42, 1, car.getId());

        assertThat(reimport.getImported()).isZero();
        assertThat(reimport.getSkipped()).isEqualTo(1);
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();

        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("40"), BigDecimal.ZERO, 0,
                null, null, null, null, MIDNIGHT, DataSource.SPRITMONITOR_IMPORT, ChargingType.AC, null));
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(1);
    }
}
