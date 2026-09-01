package com.evmonitor.infrastructure.web;

import com.evmonitor.application.savings.ChargingSavings;
import com.evmonitor.application.savings.ChargingSavingsResponse;
import com.evmonitor.application.savings.HomeChargingSavingsService;
import com.evmonitor.application.savings.PriceBasis;
import com.evmonitor.application.savings.PriceSource;
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
 * Zugriffsschutz und Leerzustand des Endpoints.
 *
 * Der Nutzer kommt ausschliesslich aus dem Token - ohne Parameter gibt es keinen Weg,
 * die Zahlen eines fremden Kontos abzufragen.
 */
@ExtendWith(MockitoExtension.class)
class ChargingSavingsControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock HomeChargingSavingsService service;
    @Mock User user;
    @Mock UserPrincipal principal;

    private ChargingSavingsController controller() {
        return new ChargingSavingsController(service);
    }

    private void givenUser(boolean premium) {
        when(principal.getUser()).thenReturn(user);
        when(user.isPremium()).thenReturn(premium);
    }

    /**
     * Die Security-Config endet auf anyRequest().permitAll() - der Endpoint muss den
     * fehlenden Token deshalb selbst abfangen, statt in eine NullPointerException zu
     * laufen.
     */
    @Test
    void withoutPrincipal_isUnauthorized() {
        ResponseEntity<ChargingSavingsResponse> response = controller().get(null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void nonPremiumUser_isRejected() {
        givenUser(false);

        ResponseEntity<ChargingSavingsResponse> response = controller().get(principal);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    /** Ohne bekannten Heim- oder Vergleichspreis liefert der Endpoint nichts - statt zu raten. */
    @Test
    void withoutCalculableSavings_returnsNoContent() {
        givenUser(true);
        when(user.getId()).thenReturn(USER);
        when(service.calculate(USER)).thenReturn(null);

        assertEquals(204, controller().get(principal).getStatusCode().value());
    }

    @Test
    void premiumUser_getsSavingsWithBasis() {
        givenUser(true);
        when(user.getId()).thenReturn(USER);
        when(service.calculate(USER)).thenReturn(new ChargingSavings(
                new BigDecimal("640"),
                new PriceBasis(PriceSource.OWN_LOGS, new BigDecimal("0.27"), 12),
                new PriceBasis(PriceSource.COUNTRY, new BigDecimal("0.40"), 2659),
                new BigDecimal("172.80"), new BigDecimal("256.00"), new BigDecimal("83.20"),
                null, new BigDecimal("212.16"), null, false));

        ChargingSavingsResponse body = controller().get(principal).getBody();

        assertNotNull(body);
        assertEquals(0, new BigDecimal("83.20").compareTo(body.savingsEur()));
        assertEquals("OWN_LOGS", body.homePriceBasis());
        assertEquals("COUNTRY", body.publicPriceBasis());
        assertEquals(2659, body.publicPriceSampleSize(), "die Kachel benennt die Breite des Vergleichs");
    }
}
