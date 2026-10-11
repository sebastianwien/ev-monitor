package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthCodeVerifyRateLimitTest extends AbstractIntegrationTest {

    @MockitoBean
    private RateLimitService rateLimitService;

    // Brute-Force-Schutz: 6 Stellen sind nur mit Limit je IP sicher, nicht allein mit dem Zähler am Code
    @Test
    void verifyIsRefusedWhenTheIpLimitIsExhausted() {
        when(rateLimitService.tryConsumeLogin(any())).thenReturn(false);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/verify",
                Map.of("email", "a@example.com", "code", "123456"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
