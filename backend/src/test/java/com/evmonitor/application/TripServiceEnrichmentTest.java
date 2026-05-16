package com.evmonitor.application;

import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Verifies that {@link TripService#saveTrip} wires the temperature-enricher hook
 * correctly: registered as an afterCommit synchronization, with the right arguments,
 * and only when the trip lacks a temperature AND has at least one geohash.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceEnrichmentTest {

    @Mock EvTripRepository tripRepository;
    @Mock CarRepository carRepository;
    @Mock TemperatureEnricher temperatureEnricher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TripService tripService;

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 5, 10, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = OffsetDateTime.of(2026, 5, 10, 9, 45, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, carRepository, objectMapper, temperatureEnricher);
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
