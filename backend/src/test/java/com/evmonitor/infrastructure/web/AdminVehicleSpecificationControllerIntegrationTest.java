package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin-Tab Fahrzeug-Specs: nur Admins, Fehler als 4xx mit Feldtext, nie 500. */
class AdminVehicleSpecificationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/admin/vehicle-specifications";

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", CarBrand.CarModel.MODEL_3.name());
        body.put("batteryCapacityKwh", 79.0);
        body.put("netBatteryCapacityKwh", 75.0);
        body.put("officialRangeKm", 629);
        body.put("officialConsumptionKwhPer100km", 14.0);
        body.put("variantName", "Long Range AWD " + System.nanoTime());
        body.put("trimLevel", "Long Range");
        body.put("availableFrom", "2024-01-01");
        body.put("sourceUrl", "https://ev-database.org/car/1991");
        return body;
    }

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("spec-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_overview_listsModelsAndWorkLists() {
        User admin = createAndSaveAdminUser("spec-admin-" + System.nanoTime() + "@example.com");
        restTemplate.exchange(PATH, HttpMethod.POST, createAuthRequest(validBody(), admin.getId(), admin.getEmail()), String.class);

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"models\":", "\"modelsWithoutRow\":", "\"carsWithoutSpec\":", "\"MODEL_3\"");
    }

    @Test
    void create_netAboveGross_isBadRequestWithFieldText() {
        User admin = createAndSaveAdminUser("spec-admin-" + System.nanoTime() + "@example.com");
        Map<String, Object> body = validBody();
        body.put("netBatteryCapacityKwh", 90.0);

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.POST,
                createAuthRequest(body, admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("Netto");
    }

    @Test
    void create_twice_isConflictNot500() {
        User admin = createAndSaveAdminUser("spec-admin-" + System.nanoTime() + "@example.com");
        Map<String, Object> body = validBody();
        restTemplate.exchange(PATH, HttpMethod.POST, createAuthRequest(body, admin.getId(), admin.getEmail()), String.class);

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.POST,
                createAuthRequest(body, admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("gibt es schon");
    }

    @Test
    void delete_unknownId_isNotFound() {
        User admin = createAndSaveAdminUser("spec-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(PATH + "/" + java.util.UUID.randomUUID() + "?reason=Test",
                HttpMethod.DELETE, createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
