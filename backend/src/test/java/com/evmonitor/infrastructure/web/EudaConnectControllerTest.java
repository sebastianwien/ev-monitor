package com.evmonitor.infrastructure.web;

import com.evmonitor.application.euda.EudaAuthException;
import com.evmonitor.application.euda.EudaConnectService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fehler des Verbindens muessen als sprechende Codes ankommen - und nie als 401, das deutet das
 * Frontend als abgelaufene ev-monitor-Sitzung.
 */
@ExtendWith(MockitoExtension.class)
class EudaConnectControllerTest {

    @Mock EudaConnectService service;
    @Mock User user;

    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(userId);
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(new EudaConnectController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static final String BODY = "{\"brand\":\"skoda\",\"email\":\"a@b.de\",\"password\":\"pw\"}";

    private void failWith(RuntimeException e) {
        when(service.connect(eq(userId), eq(carId), any(), any(), any())).thenThrow(e);
    }

    @Test
    void success_returnsConnectionStatus() throws Exception {
        when(service.connect(userId, carId, "skoda", "a@b.de", "pw")).thenReturn(
                new EudaConnectService.ConnectionStatus(carId, "skoda", "a@b.de", "VIN", "ACTIVE", null, null, null));

        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value("a@b.de"));
    }

    @Test
    void invalidCredentials_is422_not401() throws Exception {
        failWith(new EudaAuthException.InvalidCredentials());
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void interactionRequired_is409() throws Exception {
        failWith(new EudaAuthException.InteractionRequired("termsAndConditions"));
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTAL_INTERACTION_REQUIRED"));
    }

    @Test
    void portalUnavailable_is503() throws Exception {
        failWith(new EudaAuthException.PortalUnavailable("down"));
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PORTAL_UNAVAILABLE"));
    }

    @Test
    void notEntitled_is403() throws Exception {
        failWith(new EudaConnectService.NotEntitledException());
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENTITLED"));
    }

    @Test
    void rateLimited_is429() throws Exception {
        failWith(new EudaConnectService.RateLimitedException());
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void foreignCar_is403() throws Exception {
        failWith(new SecurityException("Dieses Fahrzeug gehört dir nicht"));
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void connectorRejection_isPassedThrough() throws Exception {
        failWith(new EudaConnectService.ConnectorRejectedException(429, "CAPACITY_REACHED", "voll"));
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CAPACITY_REACHED"))
                .andExpect(jsonPath("$.message").value("voll"));
    }

    @Test
    void badRequest_is400() throws Exception {
        failWith(new IllegalArgumentException("Marke wird nicht unterstützt"));
        mvc.perform(post("/api/eu-data-act/cars/{carId}/connect", carId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
