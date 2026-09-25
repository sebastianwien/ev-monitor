package com.evmonitor.application.vweuda;

import com.evmonitor.application.imports.vweuda.VwEudaAutoSyncEntitlementService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VwEudaConnectServiceTest {

    private static final Map<String, String> COOKIES = Map.of("s_abc", "SESSION", "d_abc", "DEVICE");
    private static final VwEudaAutoSyncEntitlementService.Entitlement ENTITLED =
            new VwEudaAutoSyncEntitlementService.Entitlement(true, false, null);
    private static final VwEudaAutoSyncEntitlementService.Entitlement NOT_ENTITLED =
            new VwEudaAutoSyncEntitlementService.Entitlement(false, false, null);

    @Mock CarRepository carRepository;
    @Mock UserRepository userRepository;
    @Mock VwEudaAutoSyncEntitlementService entitlement;
    @Mock RateLimitService rateLimit;
    @Mock VwEudaLoginClientFactory clients;
    @Mock VwEudaLoginClient client;
    @Mock RestTemplate rest;
    @Mock User user;
    @Mock Car car;

    private VwEudaConnectService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VwEudaConnectService(carRepository, userRepository, entitlement, rateLimit, clients, rest,
                "http://connectors:8081", "internal-secret");
        lenient().when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        lenient().when(car.getUserId()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(entitlement.entitlementFor(user)).thenReturn(ENTITLED);
        lenient().when(rateLimit.tryConsumeVwEudaLogin(any())).thenReturn(true);
        lenient().when(clients.create(any())).thenReturn(client);
        lenient().when(client.login(any(), any())).thenReturn(COOKIES);
    }

    private VwEudaConnectService.ConnectionStatus status() {
        return new VwEudaConnectService.ConnectionStatus(carId, "skoda", "max@example.com", "TMBTESTVIN0000001",
                "ACTIVE", null, null, null);
    }

    @Test
    void connect_logsIn_andHandsOnlySsoCookiesToConnector() {
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(), eq(VwEudaConnectService.ConnectionStatus.class)))
                .thenReturn(ResponseEntity.ok(status()));

        VwEudaConnectService.ConnectionStatus result = service.connect(userId, carId, "skoda", " Max@Example.com ", "geheim");

        verify(clients).create("skoda");
        verify(client).login("max@example.com", "geheim");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<VwEudaConnectService.SessionHandover>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).exchange(eq("http://connectors:8081/api/internal/euda/cars/" + carId + "/session"),
                eq(HttpMethod.PUT), entity.capture(), eq(VwEudaConnectService.ConnectionStatus.class));
        VwEudaConnectService.SessionHandover body = entity.getValue().getBody();
        assertNotNull(body);
        assertEquals(userId, body.userId());
        assertEquals("skoda", body.brand());
        assertEquals("max@example.com", body.email());
        assertEquals(COOKIES, body.idpCookies());
        assertFalse(body.toString().contains("geheim"), "Passwort darf den Core nicht verlassen");
        assertEquals("internal-secret", entity.getValue().getHeaders().getFirst("X-Internal-Token"));
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void connect_rejectsForeignCar_withoutLogin() {
        when(car.getUserId()).thenReturn(UUID.randomUUID());

        assertThrows(SecurityException.class, () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
        verifyNoInteractions(clients, rest);
    }

    @Test
    void connect_rejectsUnknownCar_withoutLogin() {
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
        verifyNoInteractions(clients, rest);
    }

    @Test
    void connect_rejectsNotEntitled_withoutLogin() {
        when(entitlement.entitlementFor(user)).thenReturn(NOT_ENTITLED);

        assertThrows(VwEudaConnectService.NotEntitledException.class,
                () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
        verifyNoInteractions(clients, rest);
    }

    @Test
    void connect_rateLimited_withoutLogin() {
        when(rateLimit.tryConsumeVwEudaLogin(userId.toString())).thenReturn(false);

        assertThrows(VwEudaConnectService.RateLimitedException.class,
                () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
        verifyNoInteractions(clients, rest);
    }

    @Test
    void connect_rejectsBlankCredentialsAndUnknownBrand_withoutLogin() {
        assertThrows(IllegalArgumentException.class, () -> service.connect(userId, carId, "skoda", " ", "pw"));
        assertThrows(IllegalArgumentException.class, () -> service.connect(userId, carId, "skoda", "a@b.de", ""));
        assertThrows(IllegalArgumentException.class, () -> service.connect(userId, carId, "tesla", "a@b.de", "pw"));
        verifyNoInteractions(clients, rest);
    }

    @Test
    void connect_acceptsVwAliasForVolkswagen() {
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(), eq(VwEudaConnectService.ConnectionStatus.class)))
                .thenReturn(ResponseEntity.ok(status()));

        service.connect(userId, carId, "VW", "a@b.de", "pw");

        verify(clients).create("volkswagen");
    }

    @Test
    void connect_whenLoginFails_doesNotCallConnector() {
        when(client.login(any(), any())).thenThrow(new VwEudaAuthException.InvalidCredentials());

        assertThrows(VwEudaAuthException.InvalidCredentials.class,
                () -> service.connect(userId, carId, "skoda", "a@b.de", "falsch"));
        verifyNoInteractions(rest);
    }

    @Test
    void connect_passesConnectorRejectionThrough() {
        String body = "{\"code\":\"CAPACITY_REACHED\",\"message\":\"voll\"}";
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(), eq(VwEudaConnectService.ConnectionStatus.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "429", null,
                        body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        VwEudaConnectService.ConnectorRejectedException ex = assertThrows(VwEudaConnectService.ConnectorRejectedException.class,
                () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
        assertEquals(429, ex.status());
        assertEquals("CAPACITY_REACHED", ex.code());
        assertEquals("voll", ex.getMessage());
    }

    @Test
    void connect_whenConnectorUnreachable_reportsPortalUnavailable() {
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(), eq(VwEudaConnectService.ConnectionStatus.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(VwEudaAuthException.PortalUnavailable.class,
                () -> service.connect(userId, carId, "skoda", "a@b.de", "pw"));
    }
}
