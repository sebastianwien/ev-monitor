package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnsubscribeControllerTest extends AbstractIntegrationTest {

    @Test
    void oneClickPost_disablesMailsWithoutLogin() {
        User user = createAndSaveUser("unsub-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        String token = jwtService.generateUnsubscribeToken(user.getEmail());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Void> response = restTemplate.postForEntity("/api/unsubscribe?token=" + token,
                new HttpEntity<>("List-Unsubscribe=One-Click", headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailNotificationsEnabled()).isFalse();
    }

    @Test
    void oneClickPost_withInvalidToken_answersOkAndChangesNothing() {
        User user = createAndSaveUser("unsub-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        ResponseEntity<Void> response = restTemplate.postForEntity("/api/unsubscribe?token=not-a-token", null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailNotificationsEnabled()).isTrue();
    }
}
