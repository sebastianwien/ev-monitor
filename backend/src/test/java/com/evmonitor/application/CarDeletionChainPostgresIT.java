package com.evmonitor.application;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Charakterisierung der Löschkette eines Autos im Core (R2-Nacharbeit "Ein Ausgang", Stand vor dem
 * Umbau) gegen das echte Schema: Soft-Delete, Purge nach dem Restore-Fenster und was dabei per
 * Fremdschlüssel mitgeht und was nicht. H2 kennt die FKs nicht, deshalb Postgres. Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CarDeletionChainPostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired CarService carService;
    @Autowired IngestGateway gateway;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(TestDataBuilder.createTestUser(
                "car-del-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        userId = user.getId();
        Car car = carRepository.save(TestDataBuilder.createTestCar(userId, CarBrand.CarModel.ID_3, new BigDecimal("58")));
        carId = car.getId();

        jdbc.update("INSERT INTO ev_log (id, car_id, kwh_charged, logged_at, data_source, created_at, updated_at) "
                + "VALUES (?, ?, 30.0, now() - interval '3 days', 'SMARTCAR_LIVE', now(), now())", UUID.randomUUID(), carId);
        jdbc.update("INSERT INTO ev_trip (car_id, user_id, data_source, trip_started_at, distance_km) "
                + "VALUES (?, ?, 'SMARTCAR_LIVE', now() - interval '2 days', 42.0)", carId, userId);
        jdbc.update("INSERT INTO charging_session_group (car_id, total_kwh_charged, session_start, session_end) "
                + "VALUES (?, 30.0, now() - interval '3 days', now() - interval '3 days' + interval '2 hours')", carId);
        jdbc.update("INSERT INTO car_battery_soh_log (car_id, soh_percent, recorded_at, source) "
                + "VALUES (?, 95.0, current_date, 'MANUAL')", carId);
        jdbc.update("INSERT INTO import_event (id, user_id, car_id, provider, channel, data_source, outcome) "
                + "VALUES (?, ?, ?, 'SMARTCAR', 'LIVE', 'SMARTCAR_LIVE', 'IMPORTED')", UUID.randomUUID(), userId, carId);
    }

    @Test
    void softDelete_hidesCar_butKeepsAllDependentRows() {
        carService.deleteCar(carId, userId);

        assertThat(carRepository.findById(carId)).isEmpty();
        assertThat(count("car", "id")).isEqualTo(1);
        assertThat(count("ev_log", "car_id")).isEqualTo(1);
        assertThat(count("ev_trip", "car_id")).isEqualTo(1);
        assertThat(count("charging_session_group", "car_id")).isEqualTo(1);
        assertThat(count("car_battery_soh_log", "car_id")).isEqualTo(1);
    }

    @Test
    void purgeWithinRestoreWindow_keepsCar() {
        carService.deleteCar(carId, userId);
        backdateDeletion(Car.RESTORE_WINDOW_DAYS - 1);

        carService.purgeExpiredDeletedCars();

        assertThat(count("car", "id")).isEqualTo(1);
    }

    /**
     * Ist-Stand: Der Purge löscht das Auto hart, der FK-CASCADE nimmt Ladungen, Ladegruppen und
     * SoH-Verlauf mit, das Import-Protokoll behält die Zeile ohne Auto. Fahrten bleiben als Waisen
     * liegen, weil {@code ev_trip.car_id} keinen Fremdschlüssel hat.
     */
    @Test
    void purgeAfterRestoreWindow_cascadesCarData_butLeavesTripsAsOrphans() {
        carService.deleteCar(carId, userId);
        backdateDeletion(Car.RESTORE_WINDOW_DAYS + 1);

        carService.purgeExpiredDeletedCars();

        assertThat(count("car", "id")).isZero();
        assertThat(count("ev_log", "car_id")).isZero();
        assertThat(count("charging_session_group", "car_id")).isZero();
        assertThat(count("car_battery_soh_log", "car_id")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_event WHERE user_id = ? AND car_id IS NULL",
                Integer.class, userId)).isEqualTo(1);
        // Waise: Fahrt überlebt das Auto
        assertThat(count("ev_trip", "car_id")).isEqualTo(1);
    }

    /**
     * Ist-Stand: Ein Connector, der für ein gelöschtes Auto weiter liefert, bekommt dieselbe Antwort
     * wie für ein nie existierendes Auto (Core 404, Protokoll REJECTED). Die Connectors halten die
     * Ladung dann offen und versuchen es erneut (Prod: Ladung 930a0d2b seit 06.08.).
     */
    @Test
    void pushForSoftDeletedCar_isRejectedLikeAnUnknownCar() {
        carService.deleteCar(carId, userId);
        ChargingEntry entry = ChargingEntry.builder()
                .loggedAt(LocalDateTime.now().withSecond(0).withNano(0))
                .kwhCharged(new BigDecimal("12.0"))
                .build();

        assertThatThrownBy(() -> gateway.ingestCharging(new IngestCommand(userId, carId, DataSource.SMARTCAR_LIVE,
                IngestDoor.CONNECTOR_PUSH, List.of(entry))))
                .isInstanceOf(NotFoundException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_event WHERE user_id = ? AND outcome = 'REJECTED'",
                Integer.class, userId)).isEqualTo(1);
    }

    private void backdateDeletion(int days) {
        jdbc.update("UPDATE car SET deleted_at = ? WHERE id = ?", LocalDateTime.now().minusDays(days), carId);
    }

    private int count(String table, String column) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, carId);
    }
}
