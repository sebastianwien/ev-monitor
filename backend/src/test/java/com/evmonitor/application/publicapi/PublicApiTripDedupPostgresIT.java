package com.evmonitor.application.publicapi;

import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedup der Fahrten-Uploads gegen Postgres mit Flyway ({@code trip_started_at} ist {@code timestamptz}, H2 speichert
 * den Offset mit). Charakterisiert das heutige Verhalten vor dem IngestGateway (R2g). Übersprungen ohne Docker.
 */
class PublicApiTripDedupPostgresIT extends AbstractPostgresIT {

    @Autowired PublicApiTripService tripService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("trip-pg-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    /** Gespeichert in UTC, erneut hochgeladen mit +02:00: derselbe Zeitpunkt, also Duplikat. */
    @Test
    void storedTripBlocksSameInstantInOtherOffset() {
        tripService.createTrip(user.getId(), trip("2025-06-01T08:00:00Z", "2025-06-01T09:00:00Z"));

        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(),
                List.of(trip("2025-06-01T10:00:00+02:00", "2025-06-01T11:00:00+02:00")));

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
    }

    /** Kein Unique-Index auf (car_id, trip_started_at): das Überspringen über dem Tombstone ist allein die Dedup-Abfrage. */
    @Test
    void tombstoneBlocksReimport() {
        UUID id = tripService.createTrip(user.getId(), trip("2025-06-01T08:00:00Z", "2025-06-01T09:00:00Z")).id();
        tripService.deleteTrip(user.getId(), id);

        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(),
                List.of(trip("2025-06-01T08:00:00Z", "2025-06-01T09:00:00Z")));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(tripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId())).isEmpty();
    }

    private PublicApiTripRequest trip(String startedAt, String endedAt) {
        return new PublicApiTripRequest(car.getId(), startedAt, endedAt, new BigDecimal("10"), null, null, null, null, null);
    }
}
