package com.evmonitor.application.ingest;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.route.RouteSketcher;
import com.evmonitor.domain.weather.TemperatureEnricher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link IngestGateway#ingestTrip} wires its two afterCommit hooks correctly:
 *
 * <ul>
 *   <li>der Temperatur-Enricher, sobald die Fahrt keine Temperatur, aber mindestens einen
 *       Geohash mitbringt,</li>
 *   <li>der Router, der die Start- und Zielgegend verbindet - aber nur, wenn die Fahrt keine
 *       eigene Trace mitbringt: gefahren schlaegt gerechnet.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IngestGatewayTripTest {

    @Mock EvTripRepository tripRepository;
    @Mock CarRepository carRepository;
    @Mock TemperatureEnricher temperatureEnricher;
    @Mock RouteSketcher routeSketcher;

    @Mock com.evmonitor.application.ingest.event.ImportEventRecorder importEvents;
    @InjectMocks IngestGateway gateway;

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 5, 10, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = OffsetDateTime.of(2026, 5, 10, 9, 45, 0, 0, ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        lenient().when(tripRepository.save(any(EvTrip.class))).thenAnswer(inv -> {
            EvTrip trip = inv.getArgument(0);
            trip.setId(UUID.randomUUID());
            return trip;
        });
        lenient().when(carRepository.findById(CAR_ID))
                .thenReturn(Optional.of(Car.builder().id(CAR_ID).userId(USER_ID).build()));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void ingestTrip_missingTempWithBothGeohashes_triggersEnricherAfterCommit() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .build();

        UUID savedId = gateway.ingestTrip(req);

        // Before commit: enricher must not be touched
        verifyNoInteractions(temperatureEnricher);

        triggerAfterCommit();

        verify(temperatureEnricher).enrichTrip(
                eq(savedId),
                eq("u2ewmk"),
                eq("u33d0k"),
                eq(START.toLocalDateTime()),
                eq(END.toLocalDateTime())
        );
    }

    @Test
    void ingestTrip_missingTempWithOnlyEndGeohash_triggersEnricherWithNullStartGeohash() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash(null)
                .locationEndGeohash("u33d0k")
                .build();

        UUID savedId = gateway.ingestTrip(req);
        triggerAfterCommit();

        verify(temperatureEnricher).enrichTrip(
                eq(savedId),
                eq(null),
                eq("u33d0k"),
                eq(START.toLocalDateTime()),
                eq(END.toLocalDateTime())
        );
    }

    @Test
    void ingestTrip_withExistingTemperature_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(new BigDecimal("12.3"))
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void ingestTrip_withNoGeohashes_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash(null)
                .locationEndGeohash(null)
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void ingestTrip_withNullTripStartedAt_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .tripStartedAt(null)
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void ingestTrip_withNullTripEndedAt_stillEnrichesWithNullEnd() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .tripEndedAt(null)
                .build();

        UUID savedId = gateway.ingestTrip(req);
        triggerAfterCommit();

        verify(temperatureEnricher).enrichTrip(
                eq(savedId),
                eq("u2ewmk"),
                eq("u33d0k"),
                eq(START.toLocalDateTime()),
                eq((LocalDateTime) null)
        );
    }

    @Test
    void ingestTrip_existingTripByExternalId_skipsEnricher() {
        UUID existingId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();
        EvTrip existing = EvTrip.builder().id(existingId).externalId(externalId).build();
        when(tripRepository.findByExternalId(externalId))
                .thenReturn(java.util.Optional.of(existing));

        InternalTripRequest req = baseRequest()
                .externalId(externalId)
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .build();

        UUID returned = gateway.ingestTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
        verify(tripRepository, never()).save(any());
        org.assertj.core.api.Assertions.assertThat(returned).isEqualTo(existingId);
    }

    /**
     * Vom User gelöschte Fahrt: der Sync liefert sie erneut und darf sie weder neu anlegen
     * noch anfassen. Der Tombstone ist der Schutz gegen Wiederkehr.
     */
    @Test
    void ingestTrip_existingTripWasDeletedByUser_isNeitherRecreatedNorTouched() {
        UUID existingId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();
        EvTrip deleted = EvTrip.builder().id(existingId).externalId(externalId)
                .deletedAt(OffsetDateTime.now().minusDays(1)).build();
        when(tripRepository.findByExternalId(externalId)).thenReturn(java.util.Optional.of(deleted));

        UUID returned = gateway.ingestTrip(baseRequest().externalId(externalId).build());
        triggerAfterCommit();

        verify(tripRepository, never()).save(any());
        verify(tripRepository, never()).saveAll(any());
        verifyNoInteractions(temperatureEnricher, routeSketcher);
        org.assertj.core.api.Assertions.assertThat(returned).isEqualTo(existingId);
        org.assertj.core.api.Assertions.assertThat(deleted.getDeletedAt()).isNotNull();
    }

    // ── Router: gerechnete Linie nur ohne eigene Trace ───────────────────────

    /**
     * Ohne diese Zusicherung koennte die Zuweisung im Builder ersatzlos entfallen: die Fahrt
     * traegt die Trace dann nur auf dem Request, nie in der Zeile - und alle anderen Tests
     * blieben gruen, weil sie entweder den Request oder eine von Hand gesetzte Entity lesen.
     */
    @Test
    void ingestTrip_persistsTheTraceOnTheStoredTrip() {
        InternalTripRequest req = baseRequest()
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        gateway.ingestTrip(req);

        ArgumentCaptor<EvTrip> saved = ArgumentCaptor.forClass(EvTrip.class);
        verify(tripRepository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getTracePolyline())
                .isEqualTo("_p~iF~ps|U_ulLnnqC");
    }

    @Test
    void ingestTrip_withTrace_matchesItInsteadOfSketchingBetweenTheEnds() {
        InternalTripRequest req = baseRequest()
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .outsideTempCelsius(new BigDecimal("12.0"))
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).matchTrace(any(UUID.class), eq("_p~iF~ps|U_ulLnnqC"), eq(new BigDecimal("25.0")));
        verify(routeSketcher, never()).sketchTrip(any(), any(), any());
    }

    /**
     * Ohne beide Enden gibt es keine Skizze - dem Matching genuegt die Spur, es braucht die
     * Geohashes nicht.
     */
    @Test
    void ingestTrip_withTraceButWithoutGeohashes_stillMatches() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(new BigDecimal("12.0"))
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).matchTrace(any(UUID.class), eq("_p~iF~ps|U_ulLnnqC"), eq(new BigDecimal("25.0")));
    }

    @Test
    void ingestTrip_withoutTrace_sketchesTheRouteBetweenBothEnds() {
        InternalTripRequest req = baseRequest()
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .outsideTempCelsius(new BigDecimal("12.0"))
                .build();

        gateway.ingestTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).sketchTrip(any(UUID.class), eq("u2ewmk"), eq("u33d0k"));
    }

    /** Befund 9.7: eine Fahrt für ein fremdes Auto wird abgelehnt, bevor die Dedup antwortet. */
    @Test
    void ingestTrip_foreignCar_isRejectedBeforeDedup() {
        InternalTripRequest req = baseRequest().externalId(UUID.randomUUID()).userId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> gateway.ingestTrip(req)).isInstanceOf(ForbiddenException.class);

        verify(tripRepository, never()).findByExternalId(any());
        verify(tripRepository, never()).save(any());
    }

    /** Unbekanntes oder soft-gelöschtes Auto ({@code findById} sieht nur nicht gelöschte). */
    @Test
    void ingestTrip_unknownOrDeletedCar_isNotFound() {
        InternalTripRequest req = baseRequest().carId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> gateway.ingestTrip(req)).isInstanceOf(NotFoundException.class);

        verify(tripRepository, never()).save(any());
    }

    @Test
    void ingestTrip_withoutCar_isRejected() {
        InternalTripRequest req = baseRequest().carId(null).build();

        assertThatThrownBy(() -> gateway.ingestTrip(req)).isInstanceOf(ValidationException.class);

        verify(tripRepository, never()).save(any());
    }

    private InternalTripRequest.InternalTripRequestBuilder baseRequest() {
        return InternalTripRequest.builder()
                .userId(USER_ID)
                .carId(CAR_ID)
                .dataSource("SMARTCAR_LIVE")
                .tripStartedAt(START)
                .tripEndedAt(END)
                .socStart(new BigDecimal("80.0"))
                .socEnd(new BigDecimal("65.0"))
                .distanceKm(new BigDecimal("25.0"))
                .status("COMPLETED");
    }

    private void triggerAfterCommit() {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(TransactionSynchronization::afterCommit);
    }
}
