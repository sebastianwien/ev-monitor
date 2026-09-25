package com.evmonitor.application.ingest;

import com.evmonitor.application.ingest.api.InternalIngestRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest.ChargingSession;
import com.evmonitor.application.ingest.api.InternalIngestRequest.Trip;
import com.evmonitor.application.ingest.api.InternalIngestResponse;
import com.evmonitor.application.ingest.api.InternalIngestResponse.EntryResult;
import com.evmonitor.application.ingest.api.InternalIngestResponse.Status;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaCoinLogRepository;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * {@code /api/internal/ingest} gegen Postgres mit Flyway: nur hier gibt es die Unique-Constraints
 * ({@code uq_ev_log_car_loggedat_datasource}, {@code idx_ev_trip_external_id}), an denen ein Race am
 * Commit scheitert. Der Race wird wie in {@code ImportDoorRacePostgresIT} nachgestellt: die
 * konkurrierende Zeile steht schon, der Dedup-Check sieht sie aber nicht. Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class IngestEndpointPostgresIT {

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

    private static final LocalDateTime RACED = LocalDateTime.of(2026, 9, 10, 10, 0);
    private static final OffsetDateTime TRIP_START = OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired IngestApiService ingestApiService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired ImportEventRepository importEventRepository;
    @Autowired JpaCoinLogRepository coinLogRepository;
    @SpyBean EvLogRepository evLogRepository;
    @SpyBean EvTripRepository tripRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("ingest-it-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    @Test
    void sessionRace_isDuplicate_andTheNextSessionIsStillCreated() {
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("20.0"), null, 60,
                null, null, null, null, RACED, DataSource.SMARTCAR_LIVE, ChargingType.AC, null));
        doReturn(false).when(evLogRepository).existsByCarIdAndLoggedAtAndDataSource(any(), eq(RACED), any());

        InternalIngestResponse response = ingestApiService.ingest(request(DataSource.SMARTCAR_LIVE,
                List.of(session(RACED), session(RACED.plusHours(2))), null));

        assertThat(response.chargingSessions()).extracting(EntryResult::status)
                .containsExactly(Status.DUPLICATE, Status.CREATED);
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(2);
        // Eine Zeile je Eintrag; der Race endet im Protokoll als zurückgerollt (wie Tür 2).
        assertThat(events()).extracting(ImportEvent::getOutcome)
                .containsExactlyInAnyOrder(ImportEventOutcome.FAILED, ImportEventOutcome.IMPORTED);
    }

    @Test
    void tripRace_answersWithTheTripThatWon() {
        UUID externalId = UUID.randomUUID();
        UUID winner = ingestApiService.ingest(request(DataSource.SMARTCAR_LIVE, null, List.of(trip(externalId))))
                .trips().get(0).id();
        Optional<EvTrip> stored = tripRepository.findByExternalId(externalId);
        // Erst übersieht die Dedup die Fahrt (Race), danach findet der Fang sie.
        doReturn(Optional.empty()).doReturn(stored).when(tripRepository).findByExternalId(externalId);

        InternalIngestResponse response = ingestApiService.ingest(request(DataSource.SMARTCAR_LIVE, null, List.of(trip(externalId))));

        assertThat(response.trips()).containsExactly(EntryResult.duplicate(winner));
        assertThat(tripRepository.findAll()).filteredOn(t -> externalId.equals(t.getExternalId())).hasSize(1);
    }

    /** Regeln der Connector-Tür: Tesla bekommt Watt, Reifen werden geerbt, Kurven landen als JSONB. */
    @Test
    void connectorPushRulesApply() {
        EvLog earlier = EvLog.createNewWithSource(car.getId(), new BigDecimal("20.0"), null, 60,
                null, null, null, null, RACED.minusDays(1), DataSource.USER_LOGGED, ChargingType.AC, null);
        evLogRepository.save(earlier.toBuilder().tireType(TireType.WINTER).build());
        ChargingSession withCurve = new ChargingSession(RACED, new BigDecimal("30.0"), 45, null, null, null, null,
                null, null, "DC", "OEM_MEASURED", new BigDecimal("20"), new BigDecimal("80"), null, null, null,
                true, "Tesla Supercharger", new BigDecimal("150"),
                new InternalIngestRequest.Extras("[{\"ts\":1,\"kw\":150}]", null));

        InternalIngestResponse response = ingestApiService.ingest(request(DataSource.TESLA_LIVE, List.of(withCurve), null));

        EvLog saved = evLogRepository.findById(response.chargingSessions().get(0).id()).orElseThrow();
        assertThat(saved.getDataSource()).isEqualTo(DataSource.TESLA_LIVE);
        assertThat(saved.getTireType()).isEqualTo(TireType.WINTER);
        assertThat(saved.isHasPowerCurve()).isTrue();
        assertThat(coinLogRepository.findAllByUserId(user.getId())).hasSize(1);
    }

    private List<ImportEvent> events() {
        return importEventRepository.findAll().stream().filter(e -> user.getId().equals(e.getUserId())).toList();
    }

    private InternalIngestRequest request(DataSource source, List<ChargingSession> sessions, List<Trip> trips) {
        return new InternalIngestRequest(source, "it", user.getId(), car.getId(), sessions, trips);
    }

    private static ChargingSession session(LocalDateTime loggedAt) {
        return new ChargingSession(loggedAt, new BigDecimal("20.0"), 60, null, null, null, null, null, null,
                "AC", null, null, null, null, null, null, null, null, null, null);
    }

    private static Trip trip(UUID externalId) {
        return new Trip(externalId, TRIP_START, TRIP_START.plusMinutes(30), new BigDecimal("80"), new BigDecimal("70"),
                null, null, new BigDecimal("25.0"), null, null, new BigDecimal("12.0"), null, null, null, null, null,
                null, null, null, null);
    }
}
