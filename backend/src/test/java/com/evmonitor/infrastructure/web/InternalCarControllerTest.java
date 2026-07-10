package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /api/internal/cars/{carId}/battery-capacity.
 *
 * The endpoint serves two values: the SoH-adjusted effective capacity (existing consumers)
 * and the nominal spec capacity backing the Smartcar SoC-Anker plausibility check (R10/R11).
 */
class InternalCarControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Test
    void getBatteryCapacity_returnsEffectiveAndNominalCapacity() {
        User user = createAndSaveUser("battery-capacity@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/cars/" + car.getId() + "/battery-capacity", HttpMethod.GET,
                new HttpEntity<>(internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("batteryCapacityKwh"));
        assertEquals(0, car.getNominalNetCapacityKwh().compareTo(
                new BigDecimal(response.getBody().get("nominalNetCapacityKwh").toString())),
                "nominalNetCapacityKwh must be the un-adjusted spec value");
    }

    @Test
    void getBatteryCapacity_unknownCar_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/cars/" + UUID.randomUUID() + "/battery-capacity", HttpMethod.GET,
                new HttpEntity<>(internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
