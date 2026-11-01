package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;

class AuthCodeRateLimitTest extends AbstractIntegrationTest {

    // Wer nur die Adresse kennt, darf deren Login nicht sperren: volles Adress-Limit antwortet wie immer,
    // verschickt aber keinen neuen Code und lässt den zuletzt gemailten gültig
    @Test
    void exhaustedAddressLimitAnswersSentWithoutReplacingTheCode() {
        doReturn(true).when(rateLimitService).tryConsumeCodeRequestIp(any());
        doReturn(false).when(rateLimitService).tryConsumeCodeMail(any());

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/request",
                Map.of("email", "opfer-" + System.nanoTime() + "@example.com", "locale", "de"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SENT");
        verify(emailService, never()).sendLoginCodeEmail(any(), any(), any());
    }

    @Test
    void exhaustedIpLimitIsRefused() {
        doReturn(false).when(rateLimitService).tryConsumeCodeRequestIp(any());

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/request",
                Map.of("email", "a@example.com", "locale", "de"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // Brute-Force-Schutz: 6 Stellen sind nur mit Limit je IP sicher, nicht allein mit dem Zähler am Code
    @Test
    void verifyIsRefusedWhenTheIpLimitIsExhausted() {
        doReturn(false).when(rateLimitService).tryConsumeLogin(any());

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/verify",
                Map.of("email", "a@example.com", "code", "123456"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
