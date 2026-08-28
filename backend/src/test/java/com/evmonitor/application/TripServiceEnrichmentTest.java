package com.evmonitor.application;

import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.route.RouteSketcher;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link TripService#saveTrip} wires its two afterCommit hooks correctly:
 *
 * <ul>
 *   <li>der Temperatur-Enricher, sobald die Fahrt keine Temperatur, aber mindestens einen
 *       Geohash mitbringt,</li>
 *   <li>der Router, der die Start- und Zielgegend verbindet - aber nur, wenn die Fahrt keine
 *       eigene Trace mitbringt: gefahren schlaegt gerechnet.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TripServiceEnrichmentTest {

    @Mock EvTripRepository tripRepository;
    @Mock CarRepository carRepository;
    @Mock TemperatureEnricher temperatureEnricher;
    @Mock RouteSketcher routeSketcher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TripService tripService;

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 5, 10, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = OffsetDateTime.of(2026, 5, 10, 9, 45, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, carRepository, objectMapper, temperatureEnricher, routeSketcher);
        TransactionSynchronizationManager.initSynchronization();
        lenient().when(tripRepository.save(any(EvTrip.class))).thenAnswer(inv -> {
            EvTrip trip = inv.getArgument(0);
            trip.setId(UUID.randomUUID());
            return trip;
        });
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void saveTrip_missingTempWithBothGeohashes_triggersEnricherAfterCommit() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .build();

        UUID savedId = tripService.saveTrip(req);

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
    void saveTrip_missingTempWithOnlyEndGeohash_triggersEnricherWithNullStartGeohash() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash(null)
                .locationEndGeohash("u33d0k")
                .build();

        UUID savedId = tripService.saveTrip(req);
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
    void saveTrip_withExistingTemperature_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(new BigDecimal("12.3"))
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void saveTrip_withNoGeohashes_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash(null)
                .locationEndGeohash(null)
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void saveTrip_withNullTripStartedAt_doesNotEnrich() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .tripStartedAt(null)
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void saveTrip_withNullTripEndedAt_stillEnrichesWithNullEnd() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .tripEndedAt(null)
                .build();

        UUID savedId = tripService.saveTrip(req);
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
    void saveTrip_existingTripByExternalId_skipsEnricher() {
        UUID existingId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();
        EvTrip existing = EvTrip.builder().id(existingId).externalId(externalId).build();
        when(tripRepository.findByExternalIdAndDeletedAtIsNull(externalId))
                .thenReturn(java.util.Optional.of(existing));

        InternalTripRequest req = baseRequest()
                .externalId(externalId)
                .outsideTempCelsius(null)
                .locationStartGeohash("u2ewmk")
                .build();

        UUID returned = tripService.saveTrip(req);
        triggerAfterCommit();

        verifyNoInteractions(temperatureEnricher);
        verify(tripRepository, never()).save(any());
        org.assertj.core.api.Assertions.assertThat(returned).isEqualTo(existingId);
    }

    // ── Router: gerechnete Linie nur ohne eigene Trace ───────────────────────

    /**
     * Ohne diese Zusicherung koennte die Zuweisung im Builder ersatzlos entfallen: die Fahrt
     * traegt die Trace dann nur auf dem Request, nie in der Zeile - und alle anderen Tests
     * blieben gruen, weil sie entweder den Request oder eine von Hand gesetzte Entity lesen.
     */
    @Test
    void saveTrip_persistsTheTraceOnTheStoredTrip() {
        InternalTripRequest req = baseRequest()
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        tripService.saveTrip(req);

        ArgumentCaptor<EvTrip> saved = ArgumentCaptor.forClass(EvTrip.class);
        verify(tripRepository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getTracePolyline())
                .isEqualTo("_p~iF~ps|U_ulLnnqC");
    }

    @Test
    void saveTrip_withTrace_matchesItInsteadOfSketchingBetweenTheEnds() {
        InternalTripRequest req = baseRequest()
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .outsideTempCelsius(new BigDecimal("12.0"))
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).matchTrace(any(UUID.class), eq("_p~iF~ps|U_ulLnnqC"), eq(new BigDecimal("25.0")));
        verify(routeSketcher, never()).sketchTrip(any(), any(), any());
    }

    /**
     * Ohne beide Enden gibt es keine Skizze - dem Matching genuegt die Spur, es braucht die
     * Geohashes nicht.
     */
    @Test
    void saveTrip_withTraceButWithoutGeohashes_stillMatches() {
        InternalTripRequest req = baseRequest()
                .outsideTempCelsius(new BigDecimal("12.0"))
                .tracePolyline("_p~iF~ps|U_ulLnnqC")
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).matchTrace(any(UUID.class), eq("_p~iF~ps|U_ulLnnqC"), eq(new BigDecimal("25.0")));
    }

    @Test
    void saveTrip_withoutTrace_sketchesTheRouteBetweenBothEnds() {
        InternalTripRequest req = baseRequest()
                .locationStartGeohash("u2ewmk")
                .locationEndGeohash("u33d0k")
                .outsideTempCelsius(new BigDecimal("12.0"))
                .build();

        tripService.saveTrip(req);
        triggerAfterCommit();

        verify(routeSketcher).sketchTrip(any(UUID.class), eq("u2ewmk"), eq("u33d0k"));
    }

    private InternalTripRequest.InternalTripRequestBuilder baseRequest() {
        return InternalTripRequest.builder()
                .userId(UUID.randomUUID())
                .carId(UUID.randomUUID())
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
