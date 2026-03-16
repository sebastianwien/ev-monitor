package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for session merging via the Public API.
 *
 * Covers:
 * - Single session with merge=false - no group created
 * - Single session with merge=true - group created
 * - Two single-session requests same day, merge=true - same group
 * - Batch (>1 sessions) with merge=true - no merging applied
 * - Single session next day with merge=true - new group created
 */
class PublicApiImportMergeTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKeyMergeOff;
    private String plaintextKeyMergeOn;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("api-merge-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        ApiKeyCreatedResponse keyOff = apiKeyService.createKey(user.getId(), "No Merge");
        plaintextKeyMergeOff = keyOff.plaintextKey();

        ApiKeyCreatedResponse keyOn = apiKeyService.createKey(user.getId(), "Merge On");
        plaintextKeyMergeOn = keyOn.plaintextKey();

        // Enable merge on the second key
        apiKeyService.updateMergeSessions(user.getId(), keyOn.id(), true);
    }

    @Test
    void testSingleSession_mergeOff_noGroupCreated() {
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-15T10:00:00", "kwh": 5.0 }]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKeyMergeOff);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("imported"));

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(1, logs.size());
        assertNull(logs.getFirst().getSessionGroupId(), "No group should be created when merge=false");
    }

    @Test
    void testSingleSession_mergeOn_groupCreated() {
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-16T10:00:00", "kwh": 5.0 }]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKeyMergeOn);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("imported"));

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(1, logs.size());
        assertNotNull(logs.getFirst().getSessionGroupId(), "Group should be created when merge=true");
    }

    @Test
    void testTwoSingleSessions_sameDay_mergeOn_sameGroup() {
        // First session
        String body1 = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-17T08:00:00", "kwh": 3.0 }]
                }
                """.formatted(car.getId());
        post(body1, plaintextKeyMergeOn);

        // Second session same day, 30 min later - within 90 min gap
        String body2 = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-17T09:00:00", "kwh": 4.0 }]
                }
                """.formatted(car.getId());
        post(body2, plaintextKeyMergeOn);

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(2, logs.size());

        // Both should have the same group ID
        assertNotNull(logs.get(0).getSessionGroupId());
        assertNotNull(logs.get(1).getSessionGroupId());
        assertEquals(logs.get(0).getSessionGroupId(), logs.get(1).getSessionGroupId(),
                "Sessions from the same day within merge gap should share the same group");
    }

    @Test
    void testBatchSessions_mergeOn_noGroupCreated() {
        // Batch request with 2 sessions - merge should NOT apply even when flag is on
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [
                    { "date": "2024-11-18T08:00:00", "kwh": 3.0 },
                    { "date": "2024-11-18T09:30:00", "kwh": 4.0 }
                  ]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKeyMergeOn);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().get("imported"));

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(2, logs.size());

        // No groups should be created for batch requests
        assertTrue(logs.stream().allMatch(l -> l.getSessionGroupId() == null),
                "Batch sessions should not be merged even with merge flag enabled");
    }

    @Test
    void testSingleSession_mergeOn_nextDay_newGroup() {
        // Session on day 1
        String body1 = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-19T22:00:00", "kwh": 5.0 }]
                }
                """.formatted(car.getId());
        post(body1, plaintextKeyMergeOn);

        // Session on day 2
        String body2 = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-20T09:00:00", "kwh": 5.0 }]
                }
                """.formatted(car.getId());
        post(body2, plaintextKeyMergeOn);

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(2, logs.size());

        assertNotNull(logs.get(0).getSessionGroupId());
        assertNotNull(logs.get(1).getSessionGroupId());
        assertNotEquals(logs.get(0).getSessionGroupId(), logs.get(1).getSessionGroupId(),
                "Sessions on different days should have different groups");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map> post(String jsonBody, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/sessions", HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), Map.class);
    }
}
