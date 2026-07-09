package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWebhookServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private UserRepository userRepository;

    private AdminWebhookService service;

    @BeforeEach
    void setUp() {
        service = new AdminWebhookService(restTemplate, userRepository,
                "http://connectors:8081", "secret-token");
    }

    @Test
    void webhookPagePassesInternalTokenAndReturnsRawBody() {
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("http://connectors:8081/api/internal/smartcar/webhook-log?vehicleId={vehicleId}&page={page}&size={size}"),
                eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class),
                eq("veh-1"), eq(2), eq(50)))
                .thenReturn(ResponseEntity.ok("{\"items\":[],\"hasMore\":false}"));

        String body = service.getWebhookPage("veh-1", 2, 50);

        assertThat(body).isEqualTo("{\"items\":[],\"hasMore\":false}");
        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Internal-Token")).isEqualTo("secret-token");
    }

    @Test
    void connectorsErrorMapsTo502() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class),
                any(), any(), any()))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> service.getWebhookPage("veh-1", 0, 50))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
    }

    @Test
    void connectionsAreEnrichedWithUsername() {
        UUID knownUser = UUID.randomUUID();
        UUID unknownUser = UUID.randomUUID();
        var raw = List.of(
                new AdminWebhookService.ConnectorConnection(knownUser, UUID.randomUUID(), "veh-1",
                        "VOLKSWAGEN", "ID.3", 2021, "VIN123"),
                new AdminWebhookService.ConnectorConnection(unknownUser, null, "veh-2",
                        "SKODA", "ENYAQ", 2022, null));
        when(restTemplate.exchange(
                eq("http://connectors:8081/api/internal/smartcar/webhook-log/connections"),
                eq(HttpMethod.GET), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(raw));
        User user = User.builder().id(knownUser).username("tobi").email("tobi@example.com")
                .authProvider(AuthProvider.LOCAL).role("USER").build();
        when(userRepository.findAllByIds(List.of(knownUser, unknownUser))).thenReturn(List.of(user));

        var result = service.getConnections();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).username()).isEqualTo("tobi");
        assertThat(result.get(0).smartcarVehicleId()).isEqualTo("veh-1");
        assertThat(result.get(1).username()).isNull();
        assertThat(result.get(1).make()).isEqualTo("SKODA");
    }
}
