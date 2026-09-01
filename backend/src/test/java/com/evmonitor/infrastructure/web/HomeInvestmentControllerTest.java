package com.evmonitor.infrastructure.web;

import com.evmonitor.application.savings.HomeInvestmentService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Die Wallbox-Investition, Grundlage der Amortisationszeile.
 *
 * Serverseitig validiert: ohne Obergrenze koennte ein absurder Betrag die Kachel
 * unbrauchbar machen, ohne Untergrenze liesse sich mit einem negativen Wert eine
 * Amortisation vortaeuschen, die nie stattgefunden hat.
 */
@ExtendWith(MockitoExtension.class)
class HomeInvestmentControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock HomeInvestmentService service;
    @Mock User user;
    @Mock UserPrincipal principal;

    private HomeInvestmentController controller() {
        return new HomeInvestmentController(service);
    }

    private void givenUser() {
        when(principal.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER);
    }

    /** Fuer die Ablehnungsfaelle: der Nutzer wird nie bis zur Id gelesen. */
    private void givenAuthenticated() {
        when(principal.getUser()).thenReturn(user);
    }

    @Test
    void savesValidAmount() {
        givenUser();

        ResponseEntity<Void> response = controller().update(principal,
                new HomeInvestmentController.HomeInvestmentRequest(new BigDecimal("1400.00")));

        assertEquals(204, response.getStatusCode().value());
        verify(service).update(USER, new BigDecimal("1400.00"));
    }

    /** null loescht den Wert - die Kachel zeigt dann wieder nur die laufende Ersparnis. */
    @Test
    void nullClearsTheValue() {
        givenUser();

        controller().update(principal, new HomeInvestmentController.HomeInvestmentRequest(null));

        verify(service).update(USER, null);
    }

    @Test
    void negativeAmount_isRejected() {
        givenAuthenticated();

        ResponseEntity<Void> response = controller().update(principal,
                new HomeInvestmentController.HomeInvestmentRequest(new BigDecimal("-1")));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void absurdAmount_isRejected() {
        givenAuthenticated();

        ResponseEntity<Void> response = controller().update(principal,
                new HomeInvestmentController.HomeInvestmentRequest(new BigDecimal("1000000")));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void withoutPrincipal_isUnauthorized() {
        ResponseEntity<Void> response = controller().update(null,
                new HomeInvestmentController.HomeInvestmentRequest(new BigDecimal("1400")));

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(service);
    }
}
