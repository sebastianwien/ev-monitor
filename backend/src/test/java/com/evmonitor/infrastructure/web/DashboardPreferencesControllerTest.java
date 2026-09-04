package com.evmonitor.infrastructure.web;

import com.evmonitor.application.dashboard.DashboardPreferencesService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Ausgeblendete Dashboard-Kacheln. Die Sichtbarkeit haengt am Nutzer und kommt
 * ausschliesslich aus dem Token - niemand kann die Kacheln eines fremden Kontos schalten.
 */
@ExtendWith(MockitoExtension.class)
class DashboardPreferencesControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock DashboardPreferencesService service;
    @Mock User user;
    @Mock UserPrincipal principal;

    private DashboardPreferencesController controller() {
        return new DashboardPreferencesController(service);
    }

    private void givenUser() {
        when(principal.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER);
    }

    @Test
    void dismiss_persistsForCurrentUser() {
        givenUser();

        ResponseEntity<Void> response = controller().update(principal,
                new DashboardPreferencesController.DashboardPreferencesRequest(true));

        assertEquals(204, response.getStatusCode().value());
        verify(service).setSavingsCardDismissed(USER, true);
    }

    @Test
    void restore_persistsForCurrentUser() {
        givenUser();

        controller().update(principal,
                new DashboardPreferencesController.DashboardPreferencesRequest(false));

        verify(service).setSavingsCardDismissed(USER, false);
    }

    /** Fehlt das Flag im Body, ist die Absicht unklar - nichts aendern. */
    @Test
    void missingFlag_isRejected() {
        when(principal.getUser()).thenReturn(user);

        ResponseEntity<Void> response = controller().update(principal,
                new DashboardPreferencesController.DashboardPreferencesRequest(null));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void get_returnsCurrentState() {
        givenUser();
        when(service.isSavingsCardDismissed(USER)).thenReturn(true);

        ResponseEntity<DashboardPreferencesController.DashboardPreferencesResponse> response =
                controller().get(principal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().savingsCardDismissed());
    }

    @Test
    void withoutPrincipal_isUnauthorized() {
        assertEquals(401, controller().update(null,
                new DashboardPreferencesController.DashboardPreferencesRequest(true))
                .getStatusCode().value());
        assertEquals(401, controller().get(null).getStatusCode().value());
        verifyNoInteractions(service);
    }
}
