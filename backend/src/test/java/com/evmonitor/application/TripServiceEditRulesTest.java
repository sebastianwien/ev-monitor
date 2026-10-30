package com.evmonitor.application;

import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.TripLoadExtra;
import com.evmonitor.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Was der Nutzer an einer Fahrt ändern darf: Routentyp, Notiz und Besonderheiten auch bei gemessenen Fahrten.
 * Die Sperre von Zeit, Strecke und Akku gemessener Fahrten liegt vorerst nur in der neuen Oberfläche.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceEditRulesTest {

    @Mock EvTripRepository tripRepository;
    @Mock CarRepository carRepository;
    @Mock User user;

    private TripService tripService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T08:00:00Z");

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, carRepository, new ObjectMapper(), mock(IngestGateway.class));
        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(tripRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * Die alte Liste (Prod-Frontend) schickt beim Bearbeiten jeder Fahrt Start und Ende mit. Solange sie läuft,
     * lehnt das Backend Werte gemessener Fahrten nicht ab; die neue Oberfläche sperrt die Felder selbst.
     */
    @Test
    void measuredTripAcceptsTimesFromOldList() {
        EvTrip trip = stub(trip(false));

        tripService.updateTrip(trip.getId(), user,
                new UpdateTripRequest(START, START.plusHours(1), null, "CITY", null, null, null, null));

        assertThat(trip.getRouteType()).isEqualTo("CITY");
        verify(tripRepository).save(any());
    }

    @Test
    void measuredTripAcceptsRouteTypeAndLoadExtras() {
        EvTrip trip = stub(trip(false));

        EvTripResponse response = tripService.updateTrip(trip.getId(), user,
                request(null, "HIGHWAY", List.of(TripLoadExtra.TRAILER, TripLoadExtra.ROOF_BOX, TripLoadExtra.TRAILER)));

        assertThat(trip.getRouteType()).isEqualTo("HIGHWAY");
        assertThat(response.loadExtras()).as("Duplikate fallen weg").containsExactly("TRAILER", "ROOF_BOX");
        assertThat(response.measured()).isTrue();
        assertThat(trip.getUserEditedAt()).as("Routentyp ist eine Nutzerangabe").isNotNull();
    }

    @Test
    void loadExtrasAloneDoNotMarkTripAsEdited() {
        EvTrip trip = stub(trip(false));

        tripService.updateTrip(trip.getId(), user, request(null, null, List.of(TripLoadExtra.FULL_LOAD)));

        assertThat(trip.getLoadExtras()).containsExactly("FULL_LOAD");
        assertThat(trip.getUserEditedAt()).isNull();
    }

    @Test
    void emptyListResetsLoadExtrasAndNullKeepsThem() {
        EvTrip trip = stub(trip(false));
        trip.setLoadExtras(List.of("TRAILER"));

        tripService.updateTrip(trip.getId(), user, request(null, "CITY", null));
        assertThat(trip.getLoadExtras()).containsExactly("TRAILER");

        tripService.updateTrip(trip.getId(), user, request(null, null, List.of()));
        assertThat(trip.getLoadExtras()).isEmpty();
    }

    @Test
    void manualTripAcceptsEverything() {
        EvTrip trip = stub(trip(true));

        EvTripResponse response = tripService.updateTrip(trip.getId(), user,
                new UpdateTripRequest(START.minusHours(1), START.plusHours(2), new BigDecimal("99"), "CITY",
                        new BigDecimal("90"), new BigDecimal("40"), null, List.of(TripLoadExtra.TRAILER)));

        assertThat(trip.getDistanceKm()).isEqualByComparingTo("99");
        assertThat(trip.getSocEnd()).isEqualByComparingTo("40");
        assertThat(response.loadExtras()).containsExactly("TRAILER");
        assertThat(response.measured()).isFalse();
    }

    @Test
    void mergeKeepsLoadExtrasOfBothTrips() {
        EvTrip earlier = stub(trip(false));
        earlier.setLoadExtras(List.of("TRAILER"));
        EvTrip later = stub(trip(false));
        later.setTripStartedAt(START.plusHours(2));
        later.setTripEndedAt(START.plusHours(3));
        later.setLoadExtras(List.of("ROOF_BOX", "TRAILER"));

        EvTripResponse response = tripService.mergeTrips(later.getId(), earlier.getId(), user);

        assertThat(response.loadExtras()).containsExactly("TRAILER", "ROOF_BOX");
    }

    @Test
    void foreignTripStaysNotFound() {
        EvTrip trip = trip(true);
        trip.setUserId(UUID.randomUUID());
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(trip.getId(), user, request(null, "CITY", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UpdateTripRequest request(BigDecimal distanceKm, String routeType, List<TripLoadExtra> loadExtras) {
        return new UpdateTripRequest(null, null, distanceKm, routeType, null, null, null, loadExtras);
    }

    private EvTrip stub(EvTrip trip) {
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        return trip;
    }

    private static EvTrip trip(boolean userCreated) {
        EvTrip trip = EvTrip.builder()
                .id(UUID.randomUUID())
                .carId(CAR_ID)
                .userId(USER_ID)
                .dataSource(userCreated ? "USER_CREATED" : "TESSIE")
                .tripStartedAt(START)
                .tripEndedAt(START.plusHours(1))
                .distanceKm(new BigDecimal("12.3"))
                .socStart(new BigDecimal("80"))
                .socEnd(new BigDecimal("70"))
                .userCreated(userCreated)
                .build();
        return trip;
    }
}
