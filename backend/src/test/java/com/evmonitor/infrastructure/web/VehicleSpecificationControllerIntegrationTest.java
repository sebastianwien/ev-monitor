package com.evmonitor.infrastructure.web;

import com.evmonitor.application.VehicleSpecificationResponse;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lookup-Endpoints des Spec-Controllers. Das Nutzer-Anlegen (POST) entfiel mit A2: Zeilen entstehen nur
 * im Admin-Tab, ein POST hierher bleibt ohne Handler.
 */
class VehicleSpecificationControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUpTestData() {
        testUser = createAndSaveUser("wltp-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
    }

    @Test
    void shouldLookupWltpData_Success() {
        // Given: WLTP data exists (use simple integer value)
        int uniqueSuffix = (int)(System.nanoTime() % 100);
        BigDecimal uniqueCapacity = new BigDecimal(Integer.toString(100 + uniqueSuffix));
        VehicleSpecification existing = TestDataBuilder.createTestVehicleSpecification(
                "BMW", "I4", uniqueCapacity); // Use different brand to avoid conflicts
        vehicleSpecificationRepository.save(existing);

        // When: GET /api/vehicle-specifications/lookup
        ResponseEntity<VehicleSpecificationResponse> response = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=BMW&model=I4&capacityKwh=" + uniqueCapacity,
                VehicleSpecificationResponse.class
        );

        // Then: Returns WLTP data
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BMW", response.getBody().carBrand());
        assertEquals("I4", response.getBody().carModel());
        assertEquals(0, uniqueCapacity.compareTo(response.getBody().batteryCapacityKwh()));
    }

    @Test
    void shouldLookupWltpData_NotFound() {
        // When: Lookup non-existent WLTP data
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=UNKNOWN&model=UNKNOWN&capacityKwh=100.0",
                String.class
        );

        // Then: Returns 404
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void userCreate_isGone_evenWhenLoggedIn() {
        Map<String, Object> body = Map.of("carBrand", "LUCID", "carModel", "AIR", "batteryCapacityKwh", 112,
                "officialRangeKm", 800, "officialConsumptionKwhPer100km", 15.0);

        ResponseEntity<String> response = restTemplate.exchange("/api/vehicle-specifications", HttpMethod.POST,
                createAuthRequest(body, userId, testUser.getEmail()), String.class);

        assertTrue(response.getStatusCode().is4xxClientError(), "POST darf keine Zeile mehr anlegen");
        assertNotEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void shouldAllowPublicLookup_WithoutAuthentication() {
        // Given: WLTP data exists (use simple integer value)
        int uniqueSuffix = (int)(System.nanoTime() % 100);
        BigDecimal uniqueCapacity = new BigDecimal(Integer.toString(300 + uniqueSuffix));
        VehicleSpecification existing = TestDataBuilder.createTestVehicleSpecification(
                "KIA", "EV6", uniqueCapacity); // Use different brand
        vehicleSpecificationRepository.save(existing);

        // When: Lookup without JWT token (public endpoint)
        ResponseEntity<VehicleSpecificationResponse> response = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=KIA&model=EV6&capacityKwh=" + uniqueCapacity,
                VehicleSpecificationResponse.class
        );

        // Then: Public access allowed
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void shouldHandleDifferentBatteryCapacities() {
        // Given: Same model with different battery capacities - use unique brand not in seed data
        String uniqueBrand = "TESTBRAND_CAPS_" + userId.toString().substring(0, 8);
        vehicleSpecificationRepository.save(TestDataBuilder.createTestVehicleSpecification(
                uniqueBrand, "TESTMODEL_B", new BigDecimal("57.5")));
        vehicleSpecificationRepository.save(TestDataBuilder.createTestVehicleSpecification(
                uniqueBrand, "TESTMODEL_B", new BigDecimal("75.0")));
        vehicleSpecificationRepository.save(TestDataBuilder.createTestVehicleSpecification(
                uniqueBrand, "TESTMODEL_B", new BigDecimal("79.0")));

        // When: Lookup specific capacity
        ResponseEntity<VehicleSpecificationResponse> response57 = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=" + uniqueBrand + "&model=TESTMODEL_B&capacityKwh=57.5",
                VehicleSpecificationResponse.class
        );
        ResponseEntity<VehicleSpecificationResponse> response75 = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=" + uniqueBrand + "&model=TESTMODEL_B&capacityKwh=75.0",
                VehicleSpecificationResponse.class
        );
        ResponseEntity<VehicleSpecificationResponse> response79 = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=" + uniqueBrand + "&model=TESTMODEL_B&capacityKwh=79.0",
                VehicleSpecificationResponse.class
        );

        // Then: Each capacity returns correct data
        assertEquals(HttpStatus.OK, response57.getStatusCode());
        assertEquals(HttpStatus.OK, response75.getStatusCode());
        assertEquals(HttpStatus.OK, response79.getStatusCode());

        assertEquals(0, new BigDecimal("57.5").compareTo(response57.getBody().batteryCapacityKwh()));
        assertEquals(0, new BigDecimal("75.0").compareTo(response75.getBody().batteryCapacityKwh()));
        assertEquals(0, new BigDecimal("79.0").compareTo(response79.getBody().batteryCapacityKwh()));
    }

    @Test
    void shouldLookupEpaData_WhenRatingSourceEpa() {
        // Given: EPA VehicleSpecification saved directly via repository
        VehicleSpecification epaSpec = VehicleSpecification.createNew(
                "RIVIAN", "R1T",
                new BigDecimal("135.0"),
                new BigDecimal("505"),
                new BigDecimal("26.7"),
                VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.EPA
        );
        vehicleSpecificationRepository.save(epaSpec);

        // When: Lookup with ratingSource=EPA
        ResponseEntity<VehicleSpecificationResponse> epaResponse = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=RIVIAN&model=R1T&capacityKwh=135.0&ratingSource=EPA",
                VehicleSpecificationResponse.class
        );

        // Then: EPA data returned
        assertEquals(HttpStatus.OK, epaResponse.getStatusCode());
        assertNotNull(epaResponse.getBody());
        assertEquals("RIVIAN", epaResponse.getBody().carBrand());

        // And: WLTP lookup for same brand/model/capacity returns 404 (different ratingSource)
        ResponseEntity<String> wltpResponse = restTemplate.getForEntity(
                "/api/vehicle-specifications/lookup?brand=RIVIAN&model=R1T&capacityKwh=135.0&ratingSource=WLTP",
                String.class
        );
        assertEquals(HttpStatus.NOT_FOUND, wltpResponse.getStatusCode());
    }
}
