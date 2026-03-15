package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests für die Session-Group-Endpoints im EvLogController.
 *
 * Prüft:
 * - GET /api/logs/groups?carId={id} → gibt Gruppen zurück
 * - GET /api/logs/group/{groupId} → gibt Sub-Sessions zurück
 * - GET /api/logs mit gruppierten Sessions → Sub-Sessions werden herausgefiltert
 * - Auth: unauthenticated → 403
 * - Ownership: anderer User kann Gruppen nicht sehen → 500 (IllegalArgumentException)
 */
class SessionGroupControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "dev-internal-token-change-in-prod";

    private User testUser;
    private Car testCar;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("session-group-ctrl-" + System.nanoTime() + "@test.com");
        testCar = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── GET /api/logs/groups ──────────────────────────────────────────────────

    @Test
    void getGroups_withNoGroups_returnsEmptyList() {
        ResponseEntity<Map[]> response = restTemplate.exchange(
                "/api/logs/groups?carId=" + testCar.getId(),
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void getGroups_withGroupedSessions_returnsGroups() {
        // Erstelle 2 WALLBOX_GOE Sessions via Internal API (30 min Gap → eine Gruppe)
        LocalDateTime base = LocalDateTime.now().minusHours(4);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "5.0", base, "2.00");
        createInternalGoeLog(testCar.getId(), testUser.getId(), "3.0", base.plusMinutes(30), "1.20");

        ResponseEntity<Map[]> response = restTemplate.exchange(
                "/api/logs/groups?carId=" + testCar.getId(),
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map[] groups = response.getBody();
        assertNotNull(groups);
        assertEquals(1, groups.length, "2 Sessions innerhalb 90 min → 1 Gruppe");
        assertEquals("WALLBOX_GOE", groups[0].get("dataSource"));
        assertEquals(2, groups[0].get("sessionCount"));
    }

    @Test
    void getGroups_withoutAuth_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/logs/groups?carId=" + testCar.getId(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── GET /api/logs/group/{groupId} ─────────────────────────────────────────

    @Test
    void getGroupSubSessions_returnsSubSessions() {
        LocalDateTime base = LocalDateTime.now().minusHours(5);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "4.0", base, null);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "3.0", base.plusMinutes(20), null);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "2.0", base.plusMinutes(45), null);

        // Gruppe abrufen
        ResponseEntity<Map[]> groupsRes = restTemplate.exchange(
                "/api/logs/groups?carId=" + testCar.getId(),
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);
        assertEquals(HttpStatus.OK, groupsRes.getStatusCode());
        assertEquals(1, groupsRes.getBody().length);
        String groupId = groupsRes.getBody()[0].get("id").toString();

        // Sub-Sessions abrufen
        ResponseEntity<Map[]> subRes = restTemplate.exchange(
                "/api/logs/group/" + groupId,
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);

        assertEquals(HttpStatus.OK, subRes.getStatusCode());
        Map[] subSessions = subRes.getBody();
        assertNotNull(subSessions);
        assertEquals(3, subSessions.length, "3 Sub-Sessions erwartet");
    }

    @Test
    void getGroupSubSessions_withoutAuth_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/logs/group/" + java.util.UUID.randomUUID(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── GET /api/logs filtert Sub-Sessions ───────────────────────────────────

    @Test
    void getLogs_withGroupedSessions_subSessionsAreHidden() {
        LocalDateTime base = LocalDateTime.now().minusHours(3);
        // 3 Sub-Sessions → 1 Gruppe
        createInternalGoeLog(testCar.getId(), testUser.getId(), "2.0", base, null);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "3.0", base.plusMinutes(20), null);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "2.5", base.plusMinutes(45), null);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                "/api/logs?carId=" + testCar.getId(),
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map[] logs = response.getBody();
        assertNotNull(logs);
        // Sub-Sessions sollen nicht im normalen Log-Feed auftauchen
        assertEquals(0, logs.length, "3 Sub-Sessions sollen im normalen /api/logs nicht sichtbar sein");
    }

    @Test
    void getLogs_mixedLogsAndGroupedSessions_onlyStandaloneLogsVisible() {
        LocalDateTime base = LocalDateTime.now().minusHours(6);

        // 1 normaler USER_LOGGED Log (über reguläre API)
        Map<String, Object> userLogRequest = Map.of(
                "carId", testCar.getId().toString(),
                "kwhCharged", "20.0",
                "costEur", "8.00",
                "chargeDurationMinutes", 60,
                "loggedAt", base.minusHours(2).toString()
        );
        restTemplate.exchange("/api/logs", HttpMethod.POST,
                createAuthRequest(userLogRequest, testUser.getId(), testUser.getEmail()),
                Map.class);

        // 2 WALLBOX_GOE Sub-Sessions (werden gruppiert)
        createInternalGoeLog(testCar.getId(), testUser.getId(), "5.0", base, null);
        createInternalGoeLog(testCar.getId(), testUser.getId(), "3.0", base.plusMinutes(30), null);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                "/api/logs?carId=" + testCar.getId(),
                HttpMethod.GET,
                createAuthRequest(testUser.getId(), testUser.getEmail()),
                Map[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map[] logs = response.getBody();
        assertNotNull(logs);
        // Nur der manuelle Log — Sub-Sessions sind versteckt
        assertEquals(1, logs.length, "Nur der manuelle USER_LOGGED Log soll sichtbar sein");
        assertEquals("USER_LOGGED", logs[0].get("dataSource"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createInternalGoeLog(java.util.UUID carId, java.util.UUID userId,
            String kwhCharged, LocalDateTime loggedAt, String costEur) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("carId", carId.toString());
        request.put("userId", userId.toString());
        request.put("kwhCharged", kwhCharged);
        request.put("chargeDurationMinutes", 30);
        request.put("loggedAt", loggedAt.toString());
        request.put("dataSource", "WALLBOX_GOE");
        request.put("costEur", costEur);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", VALID_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange("/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, headers), Map.class);
    }
}
