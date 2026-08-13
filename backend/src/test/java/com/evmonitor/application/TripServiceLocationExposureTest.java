package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Location data is exposed as sparingly as possible: the dashboard renders a map
 * background for the most recent trip only, so only that trip carries geohashes -
 * every older trip in the same response stays location-free.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceLocationExposureTest {

    @Mock EvTripRepository tripRepository;
    @Mock CarRepository carRepository;
    @Mock TemperatureEnricher temperatureEnricher;
    @Mock Car car;
    @Mock User user;

    private TripService tripService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, carRepository, new ObjectMapper(), temperatureEnricher);
        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(user.canViewLiveTrips(any())).thenReturn(true);
        lenient().when(user.canBypassEligibilityGate()).thenReturn(true);
        lenient().when(car.getUserId()).thenReturn(USER_ID);
        lenient().when(car.getModel()).thenReturn(CarBrand.CarModel.MODEL_3);
        lenient().when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
    }

    @Test
    void onlyTheNewestTripCarriesItsGeohashes() {
        EvTrip newest = trip("2026-08-06T10:00:00Z", "u33d0ke9x", "u33d0m");
        EvTrip older = trip("2026-08-01T10:00:00Z", "u2ewmk", "u2ewmn");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest, older));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        EvTripResponse newestResponse = responses.stream().filter(r -> r.id().equals(newest.getId())).findFirst().orElseThrow();
        EvTripResponse olderResponse = responses.stream().filter(r -> r.id().equals(older.getId())).findFirst().orElseThrow();
        // Precision is capped at geohash-6 (~600 m) even though the row stores more.
        assertThat(newestResponse.locationStartGeohash())
                .as("laengere Hashes werden auf 8 Stellen (~38 m) gekappt")
                .isEqualTo("u33d0ke9");
        assertThat(newestResponse.locationEndGeohash())
                .as("kuerzere Hashes bleiben unveraendert")
                .isEqualTo("u33d0m");
        assertThat(olderResponse.locationStartGeohash()).isNull();
        assertThat(olderResponse.locationEndGeohash()).isNull();
    }

    @Test
    void newestIsPickedByTripEndTimeNotByListOrder() {
        EvTrip newest = trip("2026-08-06T10:00:00Z", "u33d0k", "u33d0m");
        EvTrip older = trip("2026-08-01T10:00:00Z", "u2ewmk", "u2ewmn");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(older, newest));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        assertThat(responses.get(0).locationStartGeohash()).isNull();
        assertThat(responses.get(1).locationStartGeohash()).isEqualTo("u33d0k");
    }

    @Test
    void tripsWithoutEndTimestampNeverWin() {
        EvTrip openEnded = trip(null, "u2ewmk", "u2ewmn");
        EvTrip newest = trip("2026-08-06T10:00:00Z", "u33d0k", "u33d0m");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(openEnded, newest));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        assertThat(responses.get(0).locationStartGeohash()).isNull();
        assertThat(responses.get(1).locationStartGeohash()).isEqualTo("u33d0k");
    }

    private EvTrip trip(String endedAt, String startGeohash, String endGeohash) {
        EvTrip trip = new EvTrip();
        trip.setId(UUID.randomUUID());
        trip.setCarId(CAR_ID);
        trip.setUserId(USER_ID);
        trip.setTripEndedAt(endedAt == null ? null : OffsetDateTime.parse(endedAt).withOffsetSameInstant(ZoneOffset.UTC));
        trip.setLocationStartGeohash(startGeohash);
        trip.setLocationEndGeohash(endGeohash);
        return trip;
    }
}
