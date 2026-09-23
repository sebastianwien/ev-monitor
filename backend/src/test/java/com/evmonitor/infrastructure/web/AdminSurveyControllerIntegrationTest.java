package com.evmonitor.infrastructure.web;

import com.evmonitor.application.SurveyService;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminSurveyControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SurveyService surveyService;

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("survey-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/surveys", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void admin_seesSummaryAndResponsesWithoutUserId() {
        User admin = createAndSaveAdminUser("survey-admin-" + System.nanoTime() + "@example.com");
        User u1 = createAndSaveUser("survey-u1-" + System.nanoTime() + "@example.com");
        User u2 = createAndSaveUser("survey-u2-" + System.nanoTime() + "@example.com");
        String slug = "test-" + System.nanoTime();
        surveyService.submit(slug, u1.getId(), Map.of("satisfaction", "4", "issues", List.of("a", "b")));
        surveyService.submit(slug, u2.getId(), Map.of("satisfaction", "1", "issues_detail", "text"));

        ResponseEntity<List<AdminSurveyController.SurveySummary>> summary = restTemplate.exchange(
                "/api/admin/surveys", HttpMethod.GET, createAuthRequest(admin.getId(), admin.getEmail()),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, summary.getStatusCode());
        AdminSurveyController.SurveySummary row = summary.getBody().stream()
                .filter(s -> s.slug().equals(slug)).findFirst().orElseThrow();
        assertEquals(2, row.responses());

        ResponseEntity<String> raw = restTemplate.exchange("/api/admin/surveys/" + slug, HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertEquals(HttpStatus.OK, raw.getStatusCode());
        assertTrue(raw.getBody().contains("\"satisfaction\":\"4\""));
        assertTrue(raw.getBody().contains("\"issues\":[\"a\",\"b\"]"));
        assertTrue(raw.getBody().contains("createdAt"));
        assertFalse(raw.getBody().contains("userId"));
        assertFalse(raw.getBody().contains(u1.getId().toString()));
    }
}
