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
 * Two projection rules share one place, because both decide how much of a trip leaves
 * the backend:
 *
 * <ul>
 *   <li><b>Location</b> - die neueste Fahrt traegt ihre Geohashes fuer jeden; aeltere nur
 *       mit dem Analytics-Entitlement, hinter dem auch die Karte im Log-Feed liegt.</li>
 *   <li><b>Telemetry</b> - speeds and the climate summary are the paid analytics layer.
 *       Without the entitlement a user sees them on their latest trip only; that trip is
 *       the free teaser, the history is what SUPPORTER / AUTOSYNC_LIVE buys.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TripServiceExposureTest {

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
        tripService = new TripService(tripRepository, carRepository, new ObjectMapper(), temperatureEnricher, org.mockito.Mockito.mock(com.evmonitor.domain.route.RouteSketcher.class));
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

    // ── Telemetry (speeds + climate) ─────────────────────────────────────────

    @Test
    void withoutTheAnalyticsEntitlementOnlyTheNewestTripKeepsSpeedsAndClimate() {
        when(user.canViewLiveAnalytics()).thenReturn(false);
        EvTrip newest = tripWithTelemetry("2026-08-06T10:00:00Z");
        EvTrip older = tripWithTelemetry("2026-08-01T10:00:00Z");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest, older));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        EvTripResponse newestResponse = byId(responses, newest);
        assertThat(newestResponse.avgSpeedKmh()).isEqualByComparingTo("42.00");
        assertThat(newestResponse.maxSpeedKmh()).isEqualByComparingTo("88.00");
        assertThat(newestResponse.climate()).isNotNull();

        EvTripResponse olderResponse = byId(responses, older);
        assertThat(olderResponse.avgSpeedKmh()).isNull();
        assertThat(olderResponse.maxSpeedKmh()).isNull();
        assertThat(olderResponse.climate()).isNull();
        // Everything the gate does not cover must survive, or the list turns into a stub.
        assertThat(olderResponse.distanceKm()).isEqualByComparingTo("12.3");
    }

    @Test
    void theAnalyticsEntitlementKeepsSpeedsAndClimateOnEveryTrip() {
        when(user.canViewLiveAnalytics()).thenReturn(true);
        EvTrip newest = tripWithTelemetry("2026-08-06T10:00:00Z");
        EvTrip older = tripWithTelemetry("2026-08-01T10:00:00Z");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest, older));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        EvTripResponse olderResponse = byId(responses, older);
        assertThat(olderResponse.avgSpeedKmh()).isEqualByComparingTo("42.00");
        assertThat(olderResponse.climate()).isNotNull();
    }

    /**
     * The edit response replaces the row in the client's list, so it has to follow the same
     * rule - otherwise an empty PATCH on an old trip hands out what the list withholds.
     */
    @Test
    void editingAnOldTripDoesNotHandOutItsTelemetry() {
        when(user.canViewLiveAnalytics()).thenReturn(false);
        EvTrip older = tripWithTelemetry("2026-08-01T10:00:00Z");
        EvTrip newest = tripWithTelemetry("2026-08-06T10:00:00Z");
        when(tripRepository.findById(older.getId())).thenReturn(Optional.of(older));
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest));

        EvTripResponse response = tripService.updateTrip(older.getId(), user,
                new UpdateTripRequest(null, null, null, null, null, null, null));

        assertThat(response.avgSpeedKmh()).isNull();
        assertThat(response.climate()).isNull();
    }

    /** Editing the newest trip must not blank the map background it just had. */
    @Test
    void editingTheNewestTripKeepsItsTelemetryAndLocation() {
        when(user.canViewLiveAnalytics()).thenReturn(false);
        EvTrip newest = tripWithTelemetry("2026-08-06T10:00:00Z");
        newest.setLocationStartGeohash("u33d0ke9");
        when(tripRepository.findById(newest.getId())).thenReturn(Optional.of(newest));
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest));

        EvTripResponse response = tripService.updateTrip(newest.getId(), user,
                new UpdateTripRequest(null, null, null, null, null, null, null));

        assertThat(response.avgSpeedKmh()).isEqualByComparingTo("42.00");
        assertThat(response.climate()).isNotNull();
        assertThat(response.locationStartGeohash()).isEqualTo("u33d0ke9");
    }

    /**
     * Die Trace ist der Ortsbezug in seiner dichtesten Form - sie faellt unter dieselbe Regel
     * wie die Geohashes, sonst zeigte eine aeltere Fahrt die Strecke, deren Enden ihr die
     * Projektion gerade genommen hat.
     */
    @Test
    void onlyTheNewestTripCarriesItsTrace() {
        EvTrip newest = trip("2026-08-06T10:00:00Z", "u33d0ke9", "u33d0m");
        newest.setTracePolyline("_p~iF~ps|U_ulLnnqC");
        EvTrip older = trip("2026-08-01T10:00:00Z", "u2ewmk", "u2ewmn");
        older.setTracePolyline("_p~iF~ps|U_ulLnnqC");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest, older));

        List<EvTripResponse> responses = tripService.getTripsForCar(CAR_ID, user);

        assertThat(byId(responses, newest).tracePolyline()).isEqualTo("_p~iF~ps|U_ulLnnqC");
        assertThat(byId(responses, older).tracePolyline()).isNull();
    }

    // ── Merge: die Linie muss zu den neuen Enden passen ──────────────────────

    /**
     * Der Merge zieht Start und Ziel aus zwei Fahrten zusammen. Die Linien der Ausgangsfahrten
     * decken danach nur noch einen Teil der Strecke ab - stehen zu lassen waere eine Karte,
     * die etwas anderes zeigt als die Fahrt darunter behauptet.
     */
    @Test
    void mergingTwoTripsDropsLinesThatNoLongerSpanTheDrive() {
        EvTrip earlier = trip("2026-08-06T09:00:00Z", "u2ewmk", "u2ewmn");
        earlier.setTripStartedAt(OffsetDateTime.parse("2026-08-06T08:00:00Z"));
        earlier.setTracePolyline("_p~iF~ps|U_ulLnnqC");
        earlier.setRoutePolyline("_p~iF~ps|U_ulLnnqC");
        EvTrip later = trip("2026-08-06T12:00:00Z", "u33d0ke9", "u33d0m");
        later.setTripStartedAt(OffsetDateTime.parse("2026-08-06T11:00:00Z"));
        later.setTracePolyline("_p~iF~ps|U_ulLnnqC");
        when(tripRepository.findById(earlier.getId())).thenReturn(Optional.of(earlier));
        when(tripRepository.findById(later.getId())).thenReturn(Optional.of(later));
        when(tripRepository.save(any(EvTrip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(earlier));

        tripService.mergeTrips(earlier.getId(), later.getId(), user);

        assertThat(earlier.getLocationEndGeohash())
                .as("das Ziel kommt jetzt von der spaeteren Fahrt")
                .isEqualTo("u33d0m");
        assertThat(earlier.getTracePolyline()).isNull();
        assertThat(earlier.getRoutePolyline()).isNull();
    }

    /**
     * Die Karte aelterer Fahrten ist Teil der bezahlten Analytics-Schicht - dieselbe Grenze,
     * hinter der schon Geschwindigkeiten und Klima liegen. Ohne sie bleibt die neueste Fahrt
     * der einzige Ort mit Karte.
     */
    @Test
    void withTheAnalyticsEntitlementOlderTripsKeepTheirMapToo() {
        when(user.canViewLiveAnalytics()).thenReturn(true);
        EvTrip newest = trip("2026-08-06T10:00:00Z", "u33d0ke9", "u33d0m");
        EvTrip older = trip("2026-08-01T10:00:00Z", "u2ewmk", "u2ewmn");
        older.setTracePolyline("_p~iF~ps|U_ulLnnqC");
        when(tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                eq(USER_ID), eq(CAR_ID), any(Pageable.class))).thenReturn(List.of(newest, older));

        EvTripResponse olderResponse = byId(tripService.getTripsForCar(CAR_ID, user), older);

        assertThat(olderResponse.locationStartGeohash()).isEqualTo("u2ewmk");
        assertThat(olderResponse.locationEndGeohash()).isEqualTo("u2ewmn");
        assertThat(olderResponse.tracePolyline()).isEqualTo("_p~iF~ps|U_ulLnnqC");
    }

    private static EvTripResponse byId(List<EvTripResponse> responses, EvTrip trip) {
        return responses.stream().filter(r -> r.id().equals(trip.getId())).findFirst().orElseThrow();
    }

    private EvTrip tripWithTelemetry(String endedAt) {
        EvTrip trip = trip(endedAt, null, null);
        trip.setTripStartedAt(trip.getTripEndedAt().minusHours(1));
        trip.setDistanceKm(new java.math.BigDecimal("12.3"));
        trip.setAvgSpeedKmh(new java.math.BigDecimal("42.00"));
        trip.setMaxSpeedKmh(new java.math.BigDecimal("88.00"));
        trip.setTelemetryExtras("""
                {"tripSeconds":600,"climate":{"hvacCooling":{"active":true,"seconds":120}}}""");
        return trip;
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
