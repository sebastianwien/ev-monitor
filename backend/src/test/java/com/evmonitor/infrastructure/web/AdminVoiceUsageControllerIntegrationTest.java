package com.evmonitor.infrastructure.web;

import com.evmonitor.application.voice.VoiceUsageInsights;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftEntity;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AdminVoiceUsageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VoiceDraftRepository voiceDraftRepository;

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("voice-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/voice-usage?days=30", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void admin_seesAggregatedUsage() {
        User admin = createAndSaveAdminUser("voice-admin-" + System.nanoTime() + "@example.com");
        User u = createAndSaveUser("voice-u-" + System.nanoTime() + "@example.com");
        voiceDraftRepository.save(VoiceDraftEntity.builder().userId(u.getId()).createdAt(LocalDateTime.now().minusHours(1))
                .success(true).audioSeconds(new BigDecimal("9.50")).transcribeModel("voxtral-mini-latest").transcribeTokens(30)
                .extractModel("voxtral-small-latest").extractPromptTokens(700).extractCompletionTokens(90)
                .costUsd(new BigDecimal("0.001234")).latencyMs(1500).fieldsFilled(4).uncertainCount(1).build());

        ResponseEntity<VoiceUsageInsights> res = restTemplate.exchange("/api/admin/voice-usage?days=7", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), VoiceUsageInsights.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        VoiceUsageInsights body = res.getBody();
        assertNotNull(body);
        assertTrue(body.totals().calls() >= 1);
        assertTrue(body.totals().extractPromptTokens() >= 700);
        assertTrue(body.models().stream().anyMatch(m -> m.name().equals("voxtral-small-latest")));
        assertTrue(body.topUsers().stream().anyMatch(t -> t.userId().equals(u.getId())));
    }
}
