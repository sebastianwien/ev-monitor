package com.evmonitor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LiveEligibilityService.
 *
 * Mocks the RestTemplate so we can verify what the service does for each
 * connectors-service response shape (200 connected, 200 not connected,
 * 401, network failure, ...). Reflection injection mirrors the pattern in
 * StripeServiceTest.
 */
class LiveEligibilityServiceTest {

    private LiveEligibilityService service;
    private RestTemplate mockRest;

    @BeforeEach
    void setUp() {
        service = new LiveEligibilityService();
        mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);
        ReflectionTestUtils.setField(service, "connectorsBaseUrl", "http://test-connectors:8081");
    }

    @Test
    void returnsTrue_whenConnectorsReportsConnected() {
        whenStatusReturns(ResponseEntity.ok(Map.of("connected", true)));

        assertTrue(service.isEligibleForLive("Bearer real-token"));
    }

    @Test
    void returnsFalse_whenConnectorsReportsNotConnected() {
        whenStatusReturns(ResponseEntity.ok(Map.of("connected", false)));

        assertFalse(service.isEligibleForLive("Bearer real-token"));
    }

    @Test
    void returnsFalse_whenConnectorsReturnsNullBody() {
        whenStatusReturns(ResponseEntity.ok(null));

        assertFalse(service.isEligibleForLive("Bearer real-token"));
    }

    @Test
    void returnsFalse_whenConnectorsReturns401() {
        when(mockRest.exchange(anyStatusUrl(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        assertFalse(service.isEligibleForLive("Bearer expired-token"));
    }

    @Test
    void returnsFalse_whenConnectorsServiceUnreachable() {
        when(mockRest.exchange(anyStatusUrl(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertFalse(service.isEligibleForLive("Bearer real-token"));
    }

    @Test
    void returnsFalse_andSkipsHttpCall_whenAuthHeaderNull() {
        assertFalse(service.isEligibleForLive(null));

        verify(mockRest, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void returnsFalse_andSkipsHttpCall_whenAuthHeaderBlank() {
        assertFalse(service.isEligibleForLive("   "));

        verify(mockRest, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void forwardsAuthHeaderToConnectorsCall() {
        whenStatusReturns(ResponseEntity.ok(Map.of("connected", true)));

        service.isEligibleForLive("Bearer specific-token-xyz");

        // Capture the HttpEntity passed to RestTemplate and assert the header is verbatim.
        var captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(mockRest).exchange(anyStatusUrl(), eq(HttpMethod.GET), captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertTrue(headers.containsKey("Authorization"));
        assertTrue(headers.get("Authorization").contains("Bearer specific-token-xyz"));
    }

    @Test
    void hitsCorrectConnectorsEndpoint() {
        whenStatusReturns(ResponseEntity.ok(Map.of("connected", true)));

        service.isEligibleForLive("Bearer t");

        verify(mockRest).exchange(
                contains("/api/tesla/fleet/status"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void whenStatusReturns(ResponseEntity<?> response) {
        lenient().when(mockRest.exchange(anyStatusUrl(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) response);
    }

    private String anyStatusUrl() {
        return contains("/api/tesla/fleet/status");
    }
}
