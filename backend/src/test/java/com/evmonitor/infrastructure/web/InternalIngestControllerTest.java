package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code POST /api/internal/ingest} (Vertrag B) über HTTP: Antwortformat, Validierung, Auth, Ownership. */
class InternalIngestControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired EvLogRepository evLogRepository;
    @Autowired EvTripRepository tripRepository;

    private User owner;
    private Car car;

    @BeforeEach
    void setUp() {
        owner = createAndSaveUser("ingest-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsSessionAndTrip_thenAnswersDuplicateOnRepeat() {
        UUID externalId = UUID.randomUUID();
        Map<String, Object> body = body(owner.getId(), List.of(session("2026-09-10T08:00:00")), List.of(trip(externalId)));

        ResponseEntity<Map> first = post(body, VALID_TOKEN);
        ResponseEntity<Map> second = post(body, VALID_TOKEN);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> session = ((List<Map<String, Object>>) first.getBody().get("chargingSessions")).get(0);
        Map<String, Object> trip = ((List<Map<String, Object>>) first.getBody().get("trips")).get(0);
        assertThat(session.get("status")).isEqualTo("CREATED");
        assertThat(trip.get("status")).isEqualTo("CREATED");
        assertThat(evLogRepository.findById(UUID.fromString((String) session.get("id")))).isPresent();
        assertThat(tripRepository.findByExternalId(externalId)).isPresent();

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<Map<String, Object>>) second.getBody().get("chargingSessions")).get(0))
                .containsEntry("status", "DUPLICATE").containsEntry("id", null);
        assertThat(((List<Map<String, Object>>) second.getBody().get("trips")).get(0))
                .containsEntry("status", "DUPLICATE").containsEntry("id", trip.get("id"));
    }

    @Test
    void unknownDataSource_is400() {
        Map<String, Object> body = body(owner.getId(), List.of(session("2026-09-10T08:00:00")), null);
        body.put("dataSource", "SOMETHING_NEW");

        assertThat(post(body, VALID_TOKEN).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tripWithoutExternalId_is400_andNothingIsWritten() {
        Map<String, Object> trip = trip(UUID.randomUUID());
        trip.remove("externalId");

        ResponseEntity<Map> response = post(body(owner.getId(), List.of(session("2026-09-10T08:00:00")), List.of(trip)), VALID_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void sessionWithoutLoggedAt_is400() {
        Map<String, Object> session = session("2026-09-10T08:00:00");
        session.remove("loggedAt");

        assertThat(post(body(owner.getId(), List.of(session), null), VALID_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noEntries_is400() {
        assertThat(post(body(owner.getId(), List.of(), null), VALID_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void moreThan500Sessions_is400() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (int i = 0; i < 501; i++) sessions.add(session("2026-09-10T08:00:00"));

        assertThat(post(body(owner.getId(), sessions, null), VALID_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void withoutOrWrongToken_is403() {
        Map<String, Object> body = body(owner.getId(), List.of(session("2026-09-10T08:00:00")), null);

        assertThat(post(body, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(body, "wrong", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void foreignCar_is403_andNothingIsWritten() {
        User stranger = createAndSaveUser("ingest-stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        UUID externalId = UUID.randomUUID();

        ResponseEntity<Map> response = post(body(stranger.getId(), List.of(session("2026-09-10T08:00:00")),
                List.of(trip(externalId))), VALID_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
        assertThat(tripRepository.findByExternalId(externalId)).isEmpty();
    }

    /** Connectors meldet abgeleitete Tesla-Fahrten (InferredTripService) mit eigener Quelle. */
    @Test
    @SuppressWarnings("unchecked")
    void teslaInferredTrip_isCreated() {
        UUID externalId = UUID.randomUUID();
        Map<String, Object> body = body(owner.getId(), null, List.of(trip(externalId)));
        body.put("dataSource", "TESLA_INFERRED");

        ResponseEntity<Map> response = post(body, VALID_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<Map<String, Object>>) response.getBody().get("trips")).get(0)).containsEntry("status", "CREATED");
        assertThat(tripRepository.findByExternalId(externalId)).get()
                .extracting(t -> t.getDataSource()).isEqualTo("TESLA_INFERRED");
    }

    @Test
    void unknownCar_is404() {
        Map<String, Object> body = body(owner.getId(), null, List.of(trip(UUID.randomUUID())));
        body.put("carId", UUID.randomUUID().toString());

        assertThat(post(body, VALID_TOKEN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> post(Map<String, Object> body, String token) {
        return post(body, token, Map.class);
    }

    private <T> ResponseEntity<T> post(Map<String, Object> body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/internal/ingest", HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private Map<String, Object> body(UUID userId, List<Map<String, Object>> sessions, List<Map<String, Object>> trips) {
        Map<String, Object> body = new HashMap<>();
        body.put("dataSource", "SMARTCAR_LIVE");
        body.put("externalRef", "test");
        body.put("userId", userId.toString());
        body.put("carId", car.getId().toString());
        if (sessions != null) body.put("chargingSessions", sessions);
        if (trips != null) body.put("trips", trips);
        return body;
    }

    private static Map<String, Object> session(String loggedAt) {
        Map<String, Object> s = new HashMap<>();
        s.put("loggedAt", loggedAt);
        s.put("kwhCharged", 20.5);
        s.put("chargingType", "AC");
        s.put("extras", Map.of("socCurvePointsJson", "[{\"ts\":1,\"soc\":20}]"));
        return s;
    }

    private static Map<String, Object> trip(UUID externalId) {
        Map<String, Object> t = new HashMap<>();
        t.put("externalId", externalId.toString());
        t.put("tripStartedAt", "2026-09-10T09:00:00Z");
        t.put("tripEndedAt", "2026-09-10T09:30:00Z");
        t.put("socStart", 80);
        t.put("socEnd", 70);
        t.put("distanceKm", 25.0);
        return t;
    }
}
