package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bundesweite Spritpreise fuer Vergleichssaetze im Frontend. Ohne Tankerkoenig-Key
 * (wie im Test) kommen die Fallback-Preise, erkennbar an updatedAt = null.
 */
class PublicFuelPriceControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    @SuppressWarnings("rawtypes")
    void fuelPrices_areReachableWithoutAuthAndCarryBothFuels() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/public/fuel-prices", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map body = response.getBody();
        assertNotNull(body);
        assertTrue(((Number) body.get("petrolEurPerLitre")).doubleValue() > 0);
        assertTrue(((Number) body.get("dieselEurPerLitre")).doubleValue() > 0);
        assertTrue(body.containsKey("updatedAt"));
        assertNull(body.get("updatedAt"), "ohne API-Key gibt es keinen Abrufzeitpunkt");
        assertTrue(response.getHeaders().getCacheControl().contains("max-age"));
    }
}
