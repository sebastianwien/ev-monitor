package com.evmonitor.application.ingest;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest.ChargingSession;
import com.evmonitor.application.ingest.api.InternalIngestRequest.Extras;
import com.evmonitor.application.ingest.api.InternalIngestRequest.Trip;
import com.evmonitor.application.ingest.api.InternalIngestResponse;
import com.evmonitor.application.ingest.api.InternalIngestResponse.EntryResult;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestApiServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID CAR = UUID.randomUUID();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 10, 8, 0);
    private static final OffsetDateTime TRIP_START = OffsetDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZoneOffset.UTC);

    @Mock IngestGateway gateway;
    @Mock EvTripRepository tripRepository;
    @InjectMocks IngestApiService service;

    @Test
    void eachSessionIsItsOwnGatewayCall_withConnectorPushRules_andAnswersInRequestOrder() {
        EvLog created = EvLog.builder().id(UUID.randomUUID()).build();
        when(gateway.ingestCharging(any()))
                .thenReturn(new IngestResult(1, 0, 0, List.of(created)))
                .thenReturn(new IngestResult(0, 1, 0, List.of()));

        InternalIngestResponse response = service.ingest(request(List.of(session(AT), session(AT.plusHours(2))), null));

        assertThat(response.chargingSessions()).containsExactly(
                EntryResult.created(created.getId()), EntryResult.duplicate(null));
        assertThat(response.trips()).isEmpty();
        ArgumentCaptor<IngestCommand> commands = ArgumentCaptor.forClass(IngestCommand.class);
        verify(gateway, times(2)).ingestCharging(commands.capture());
        assertThat(commands.getAllValues()).allSatisfy(c -> {
            assertThat(c.door()).isEqualTo(IngestDoor.CONNECTOR_PUSH);
            assertThat(c.dataSource()).isEqualTo(DataSource.SMARTCAR_LIVE);
            assertThat(c.userId()).isEqualTo(USER);
            assertThat(c.carId()).isEqualTo(CAR);
            assertThat(c.entries()).hasSize(1);
        });
        assertThat(commands.getAllValues().get(1).entries().get(0).loggedAt()).isEqualTo(AT.plusHours(2));
    }

    @Test
    void sessionFields_andExtras_reachTheEntry_enumsLenient() {
        when(gateway.ingestCharging(any())).thenReturn(new IngestResult(0, 1, 0, List.of()));
        ChargingSession s = new ChargingSession(AT, new BigDecimal("41.2"), 95, "u33dc0", 15234, 15200, 15300,
                new BigDecimal("12.30"), new BigDecimal("0.299"), "HPC", "GUESSED", new BigDecimal("20"),
                new BigDecimal("80"), true, 4.5, "{}", true, "EnBW", new BigDecimal("11"),
                new Extras("[{\"ts\":1,\"kw\":11}]", null));

        service.ingest(request(List.of(s), null));

        ArgumentCaptor<IngestCommand> command = ArgumentCaptor.forClass(IngestCommand.class);
        verify(gateway).ingestCharging(command.capture());
        ChargingEntry e = command.getValue().entries().get(0);
        assertThat(e.kwhCharged()).isEqualByComparingTo("41.2");
        assertThat(e.odometerKm()).isEqualTo(15234);
        assertThat(e.odometerSuggestionMinKm()).isEqualTo(15200);
        assertThat(e.publicCharging()).isTrue();
        assertThat(e.socStartMissed()).isTrue();
        assertThat(e.chargingType()).isEqualTo(ChargingType.UNKNOWN);
        assertThat(e.energySource()).isNull();
        assertThat(e.powerCurvePointsJson()).isEqualTo("[{\"ts\":1,\"kw\":11}]");
        assertThat(e.socCurvePointsJson()).isNull();
    }

    /** Parallel angelegt: der Unique-Constraint schlägt am Commit zu, außerhalb der Transaktion gefangen. */
    @Test
    void sessionRace_isDuplicate_andLaterEntriesStillRun() {
        EvLog created = EvLog.builder().id(UUID.randomUUID()).build();
        when(gateway.ingestCharging(any()))
                .thenThrow(new DataIntegrityViolationException("uq_ev_log_car_loggedat_datasource"))
                .thenReturn(new IngestResult(1, 0, 0, List.of(created)));

        InternalIngestResponse response = service.ingest(request(List.of(session(AT), session(AT.plusHours(1))), null));

        assertThat(response.chargingSessions()).containsExactly(
                EntryResult.duplicate(null), EntryResult.created(created.getId()));
    }

    @Test
    void trips_createdOrExisting_withHeadFieldsFromTheRequest() {
        UUID newId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        when(gateway.ingestTrip(any()))
                .thenReturn(new IngestGateway.TripIngest(newId, true))
                .thenReturn(new IngestGateway.TripIngest(existingId, false));

        InternalIngestResponse response = service.ingest(request(null, List.of(trip(UUID.randomUUID()), trip(UUID.randomUUID()))));

        assertThat(response.trips()).containsExactly(EntryResult.created(newId), EntryResult.duplicate(existingId));
        ArgumentCaptor<InternalTripRequest> trips = ArgumentCaptor.forClass(InternalTripRequest.class);
        verify(gateway, times(2)).ingestTrip(trips.capture());
        assertThat(trips.getAllValues().get(0)).satisfies(t -> {
            assertThat(t.userId()).isEqualTo(USER);
            assertThat(t.carId()).isEqualTo(CAR);
            assertThat(t.dataSource()).isEqualTo("SMARTCAR_LIVE");
            assertThat(t.tripStartedAt()).isEqualTo(TRIP_START);
            assertThat(t.distanceKm()).isEqualByComparingTo("25.0");
        });
    }

    @Test
    void tripRace_answersWithTheTripThatWon() {
        UUID externalId = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        when(gateway.ingestTrip(any())).thenThrow(new DataIntegrityViolationException("idx_ev_trip_external_id"));
        when(tripRepository.findByExternalId(externalId)).thenReturn(Optional.of(EvTrip.builder().id(winner).build()));

        InternalIngestResponse response = service.ingest(request(null, List.of(trip(externalId))));

        assertThat(response.trips()).containsExactly(EntryResult.duplicate(winner));
    }

    /** Das Auto gilt für den ganzen Aufruf: der erste Eintrag scheitert, nichts danach läuft. */
    @Test
    void foreignCar_stopsTheWholeRequest() {
        when(gateway.ingestCharging(any())).thenThrow(new ForbiddenException("Car not owned"));

        assertThatThrownBy(() -> service.ingest(request(List.of(session(AT)), List.of(trip(UUID.randomUUID())))))
                .isInstanceOf(ForbiddenException.class);

        verify(gateway, never()).ingestTrip(any());
    }

    private static InternalIngestRequest request(List<ChargingSession> sessions, List<Trip> trips) {
        return new InternalIngestRequest(DataSource.SMARTCAR_LIVE, "drop-1", USER, CAR, sessions, trips);
    }

    private static ChargingSession session(LocalDateTime loggedAt) {
        return new ChargingSession(loggedAt, new BigDecimal("20.0"), null, null, null, null, null, null, null,
                "AC", null, null, null, null, null, null, null, null, null, null);
    }

    private static Trip trip(UUID externalId) {
        return new Trip(externalId, TRIP_START, TRIP_START.plusMinutes(30), new BigDecimal("80"), new BigDecimal("70"),
                null, null, new BigDecimal("25.0"), null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
