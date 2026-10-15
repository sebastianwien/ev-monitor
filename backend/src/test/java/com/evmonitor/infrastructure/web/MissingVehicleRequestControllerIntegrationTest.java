package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Meldeweg "Fehlt etwas?": nur mit Login, Längen serverseitig, Admin-Liste und Erledigen nur für Admins. */
class MissingVehicleRequestControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_PATH = "/api/vehicle-requests";
    private static final String ADMIN_PATH = "/api/admin/vehicle-requests";

    @Autowired
    private MissingVehicleRequestRepository requestRepository;

    private static Map<String, Object> body(String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("freeText", text);
        body.put("contactEmail", "max@example.com");
        body.put("brand", "POLESTAR");
        body.put("model", "POLESTAR_2");
        body.put("netKwh", 78);
        body.put("buildYear", 2022);
        return body;
    }

    @Test
    void post_withoutLogin_isRejected() {
        ResponseEntity<String> res = restTemplate.postForEntity(USER_PATH, body("Lucid Air"), String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void post_withLogin_storesRequest() {
        User user = createAndSaveUser("req-" + System.nanoTime() + "@example.com");
        String text = "Lucid Air " + System.nanoTime();

        ResponseEntity<String> res = restTemplate.exchange(USER_PATH, HttpMethod.POST,
                createAuthRequest(body(text), user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<MissingVehicleRequest> open = requestRepository.findAllByDoneAtIsNullOrderByCreatedAtDesc();
        assertThat(open).anySatisfy(r -> {
            assertThat(r.getFreeText()).isEqualTo(text);
            assertThat(r.getUserId()).isEqualTo(user.getId());
        });
    }

    @Test
    void post_textTooLong_isBadRequest() {
        User user = createAndSaveUser("req-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(USER_PATH, HttpMethod.POST,
                createAuthRequest(body("x".repeat(151)), user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminList_nonAdmin_isForbidden() {
        User user = createAndSaveUser("req-" + System.nanoTime() + "@example.com");
        ResponseEntity<String> res = restTemplate.exchange(ADMIN_PATH, HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminList_showsOpenRequests_andDoneRemovesThem() {
        User user = createAndSaveUser("req-" + System.nanoTime() + "@example.com");
        User admin = createAndSaveAdminUser("req-admin-" + System.nanoTime() + "@example.com");
        String text = "Lucid Air " + System.nanoTime();
        restTemplate.exchange(USER_PATH, HttpMethod.POST,
                createAuthRequest(body(text), user.getId(), user.getEmail()), String.class);

        ResponseEntity<String> list = restTemplate.exchange(ADMIN_PATH, HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(text).doesNotContain("max@example.com");

        String id = requestRepository.findAllByDoneAtIsNullOrderByCreatedAtDesc().stream()
                .filter(r -> r.getFreeText().equals(text)).findFirst().orElseThrow().getId().toString();
        ResponseEntity<String> done = restTemplate.exchange(ADMIN_PATH + "/" + id + "/done", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> again = restTemplate.exchange(ADMIN_PATH + "/" + id + "/done", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> listAfter = restTemplate.exchange(ADMIN_PATH, HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(listAfter.getBody()).doesNotContain(text);
    }
}
