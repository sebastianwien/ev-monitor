package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /api/logs/price-suggestion for a STORED charge: the client only has the log's geohash
 * (lat/lon are never persisted), so the endpoint must accept it directly and must say which
 * earlier charge the suggestion comes from.
 */
class PriceSuggestionByGeohashIntegrationTest extends AbstractIntegrationTest {

    private static final String HERE = "u1r4nt7";

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("price-geohash-" + System.nanoTime() + "@example.com");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    void suggestsFromAStoredGeohashAndNamesTheAnchorCharge() {
        LocalDateTime anchorAt = LocalDateTime.now().minusDays(4).withNano(0);
        evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("20.0"), new BigDecimal("9.80"),
                30, HERE, 10_000, null, null, anchorAt, ChargingType.AC, null, null, true, null));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/logs/price-suggestion?geohash=" + HERE + "&isPublic=true&chargingType=AC",
                HttpMethod.GET, new HttpEntity<>(createAuthHeaders(user.getId(), user.getEmail())), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(0, new BigDecimal(body.get("costPerKwh").toString()).compareTo(new BigDecimal("0.4900")));
        assertTrue(body.get("anchorLoggedAt").toString().startsWith(anchorAt.toLocalDate().toString()),
                "anchorLoggedAt should name the anchor charge, was " + body.get("anchorLoggedAt"));
    }

    @Test
    void neitherGeohashNorCoordinatesIsABadRequest() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/logs/price-suggestion?isPublic=true",
                HttpMethod.GET, new HttpEntity<>(createAuthHeaders(user.getId(), user.getEmail())), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
