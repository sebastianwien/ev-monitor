package com.evmonitor.application.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountPurgeClientTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final AccountPurgeClient client = new AccountPurgeClient(rest, "http://conn:8081", "http://wb:8090", "tok");
    private final UUID userId = UUID.randomUUID();

    @Test
    void purgeConnectors_sendsCarIdsAndToken() {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class))).thenReturn(ResponseEntity.ok().build());
        UUID carId = UUID.randomUUID();

        client.purgeConnectors(userId, List.of(carId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).exchange(eq("http://conn:8081/api/internal/users/" + userId), eq(HttpMethod.DELETE), entity.capture(), eq(Void.class));
        assertEquals(List.of(carId), entity.getValue().getBody().get("carIds"));
        assertEquals("tok", entity.getValue().getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    void purgeWallbox_callsWallboxUrlWithoutBody() {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class))).thenReturn(ResponseEntity.ok().build());

        client.purgeWallbox(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Object>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).exchange(eq("http://wb:8090/api/internal/users/" + userId), eq(HttpMethod.DELETE), entity.capture(), eq(Void.class));
        assertNull(entity.getValue().getBody());
        assertEquals("tok", entity.getValue().getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    void purge_wrapsAnyFailureInIllegalState() {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class))).thenThrow(new RuntimeException("down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.purgeWallbox(userId));
        assertTrue(ex.getMessage().contains("wallbox"));
    }
}
