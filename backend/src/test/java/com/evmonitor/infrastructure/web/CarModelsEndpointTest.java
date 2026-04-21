package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarCreateResponse;
import com.evmonitor.application.CarRequest;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testet GET /api/cars/brands/{brand}/models - Anreicherung mit vehicleSpecificationId
 * und POST /api/cars - Speicherung der vehicleSpecificationId am Car.
 */
class CarModelsEndpointTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(
            Math.abs(System.currentTimeMillis() % 10_000_000L));

    /** Einzigartiger kWh-Wert um Unique-Constraint-Konflikte zu vermeiden. */
    private BigDecimal uniqueKwh() {
        return new BigDecimal(800 + SEQ.getAndIncrement() + ".00");
    }

    @Test
    void vehicleSpecificationId_isSet_whenSpecExistsForCapacity() {
        BigDecimal kwh = uniqueKwh();
        VehicleSpecification spec = vehicleSpecificationRepository.save(
                VehicleSpecification.createNew("SKODA", "ENYAQ",
                        kwh, new BigDecimal("534.0"), new BigDecimal("16.50"),
                        VehicleSpecification.WltpType.COMBINED));

        List<Map<String, Object>> models = getModels("SKODA");

        Map<String, Object> enyaq = findByValue(models, "ENYAQ");
        Map<String, Object> cap = findCapacityByKwh(enyaq, kwh.doubleValue());

        assertNotNull(cap.get("vehicleSpecificationId"),
                "vehicleSpecificationId muss gesetzt sein wenn Spec vorhanden");
        assertEquals(spec.getId().toString(), cap.get("vehicleSpecificationId").toString());
    }

    @Test
    void twoSpecs_forSameModel_bothHaveVehicleSpecificationId() {
        BigDecimal kwh1 = uniqueKwh();
        BigDecimal kwh2 = uniqueKwh();

        VehicleSpecification spec1 = vehicleSpecificationRepository.save(
                VehicleSpecification.createNew("VW", "ID_3", kwh1,
                        new BigDecimal("429.0"), new BigDecimal("15.50"),
                        VehicleSpecification.WltpType.COMBINED));
        VehicleSpecification spec2 = vehicleSpecificationRepository.save(
                VehicleSpecification.createNew("VW", "ID_3", kwh2,
                        new BigDecimal("549.0"), new BigDecimal("15.80"),
                        VehicleSpecification.WltpType.COMBINED));

        List<Map<String, Object>> models = getModels("VW");
        Map<String, Object> id3 = findByValue(models, "ID_3");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> capacities = (List<Map<String, Object>>) id3.get("capacities");

        Map<String, Object> cap1 = findCapacityByKwh(id3, kwh1.doubleValue());
        Map<String, Object> cap2 = findCapacityByKwh(id3, kwh2.doubleValue());

        assertEquals(spec1.getId().toString(), cap1.get("vehicleSpecificationId").toString());
        assertEquals(spec2.getId().toString(), cap2.get("vehicleSpecificationId").toString());

        // Beide IDs müssen unterschiedlich sein
        assertNotEquals(cap1.get("vehicleSpecificationId"), cap2.get("vehicleSpecificationId"));
    }

    @Test
    void carCreate_withVehicleSpecificationId_persistsIt() {
        BigDecimal kwh = uniqueKwh();
        VehicleSpecification spec = vehicleSpecificationRepository.save(
                VehicleSpecification.createNew("SKODA", "ENYAQ",
                        kwh, new BigDecimal("534.0"), new BigDecimal("16.50"),
                        VehicleSpecification.WltpType.COMBINED));

        User user = createAndSaveUser("spectest-" + System.currentTimeMillis() + "@test.com");

        CarRequest request = new CarRequest(
                CarBrand.CarModel.ENYAQ,
                2024, "SB-EV-1", "85 Sportline",
                kwh, new BigDecimal("210"),
                null, false,
                spec.getId());

        ResponseEntity<CarCreateResponse> response = restTemplate.postForEntity(
                "/api/cars",
                createAuthRequest(request, user.getId(), user.getEmail()),
                CarCreateResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(spec.getId(), response.getBody().car().vehicleSpecificationId(),
                "vehicleSpecificationId muss am gespeicherten Car ankommen");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getModels(String brand) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/cars/brands/" + brand + "/models",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findByValue(List<Map<String, Object>> list, String value) {
        return list.stream()
                .filter(m -> value.equals(m.get("value")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(value + " nicht gefunden"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCapacityByKwh(Map<String, Object> model, double kwh) {
        List<Map<String, Object>> caps = (List<Map<String, Object>>) model.get("capacities");
        return caps.stream()
                .filter(c -> {
                    double val = ((Number) c.get("kWh")).doubleValue();
                    return Math.abs(val - kwh) < 0.01;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError(kwh + " kWh nicht in Kapazitäten gefunden"));
    }
}
