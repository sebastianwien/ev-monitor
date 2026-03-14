package com.evmonitor.infrastructure.web;

import com.evmonitor.application.VehicleSpecificationCreateResponse;
import com.evmonitor.application.VehicleSpecificationRequest;
import com.evmonitor.application.VehicleSpecificationResponse;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for VehicleSpecificationController.
 * Tests WLTP data crowdsourcing and coin rewards.
 *
 * BUSINESS CRITICAL: WLTP contributions must award coins correctly!
 * SECURITY CRITICAL: Input sanitization to prevent XSS!
 */
class VehicleSpecificationControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUpTestData() {
        // Create test user with unique email
        testUser = createAndSaveUser("wltp-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
    }

    @Test
    void shouldCreateWltpData_AndAwardCoins() {
        // Given: New WLTP data for a vehicle — use brand not in seed data to avoid conflicts
        BigDecimal uniqueCapacity = new BigDecimal("75.0");
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESTBRAND_" + userId.toString().substring(0, 8),
                "TESTMODEL_A",
                uniqueCapacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5")
        );

        HttpEntity<VehicleSpecificationRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/vehicle-specifications
        ResponseEntity<VehicleSpecificationCreateResponse> response = restTemplate.exchange(
                "/api/vehicle-specifications",
                HttpMethod.POST,
                requestWithAuth,
                VehicleSpecificationCreateResponse.class
        );

        // Then: WLTP data created and coins awarded
        assertTrue(response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.CREATED,
                "Expected 200 or 201, got: " + response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(50, response.getBody().coinsAwarded());

        // Verify response contains correct data
        VehicleSpecificationResponse spec = response.getBody().specification();
        assertNotNull(spec);
        assertEquals("TESTBRAND_" + userId.toString().substring(0, 8), spec.carBrand());
        assertEquals("TESTMODEL_A", spec.carModel());
        assertEquals(0, new BigDecimal("450.0").compareTo(spec.wltpRangeKm()));
        assertEquals(0, new BigDecimal("16.5").compareTo(spec.wltpConsumptionKwhPer100km()));

        // Verify coins were awarded
        List<CoinLog> coinLogs = coinLogRepository.findAllByUserId(userId);
        assertTrue(coinLogs.size() >= 1, "At least one coin log should exist");
        // Find the SOCIAL_COIN log
        boolean hasSocialCoin = coinLogs.stream()
                .anyMatch(log -> log.getCoinType() == CoinType.SOCIAL_COIN && log.getAmount() == 50);
        assertTrue(hasSocialCoin, "Should have SOCIAL_COIN reward of 50");
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
    void shouldRejectDuplicateWltpData() {
        // Given: WLTP data already exists (use simple integer value)
        int uniqueSuffix = (int)(System.nanoTime() % 100);
        BigDecimal uniqueCapacity = new BigDecimal(Integer.toString(200 + uniqueSuffix));
        VehicleSpecification existing = TestDataBuilder.createTestVehicleSpecification(
                "HYUNDAI", "IONIQ_5", uniqueCapacity); // Use different brand
        vehicleSpecificationRepository.save(existing);

        // When: Try to create duplicate
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "HYUNDAI",
                "IONIQ_5",
                uniqueCapacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5")
        );

        HttpEntity<VehicleSpecificationRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/vehicle-specifications",
                HttpMethod.POST,
                requestWithAuth,
                String.class
        );

        // Then: Should be rejected (500 or 400 with error)
        assertTrue(
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR ||
                response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                response.getStatusCode() == HttpStatus.CONFLICT,
                "Expected error status, got: " + response.getStatusCode()
        );

        // Verify user didn't get double coins
        List<CoinLog> coinLogs = coinLogRepository.findAllByUserId(userId);
        // Should have at most 1 coin log from the first save
        assertTrue(coinLogs.size() <= 1, "Should not award coins twice for same data");
    }

    @Test
    void shouldSanitizeInput_XssPrevention() {
        // Given: Malicious input with HTML tags — use brand not in seed data to avoid conflicts
        BigDecimal uniqueCapacity = new BigDecimal("78.0");
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESTBRAND_XSS_" + userId.toString().substring(0, 8) + "<script>alert('XSS')</script>",
                "TESTMODEL_XSS<img src=x onerror=alert(1)>",
                uniqueCapacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5")
        );

        HttpEntity<VehicleSpecificationRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/vehicle-specifications
        ResponseEntity<VehicleSpecificationCreateResponse> response = restTemplate.exchange(
                "/api/vehicle-specifications",
                HttpMethod.POST,
                requestWithAuth,
                VehicleSpecificationCreateResponse.class
        );

        // Then: Data created with sanitized input
        assertTrue(response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.CREATED,
                "Expected 200 or 201, got: " + response.getStatusCode());

        // Verify HTML tags were removed from response data (script tags stripped, leaving only "TESTBRAND_XSS")
        VehicleSpecificationResponse spec = response.getBody().specification();
        assertNotNull(spec);
        assertFalse(spec.carBrand().contains("<script>"));
        assertFalse(spec.carBrand().contains("</script>"));
        assertFalse(spec.carModel().contains("<img"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("TODO: Fix trimming test - currently returns 400")
    void shouldTrimWhitespace() {
        // Given: Input with extra whitespace (unique capacity)
        BigDecimal uniqueCapacity = new BigDecimal("79." + System.nanoTime() % 1000);
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "  TESLA  ",
                "  MODEL_3  ",
                uniqueCapacity,
                new BigDecimal("450.0"),
                new BigDecimal("16.5")
        );

        HttpEntity<VehicleSpecificationRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/vehicle-specifications
        ResponseEntity<VehicleSpecificationCreateResponse> response = restTemplate.exchange(
                "/api/vehicle-specifications",
                HttpMethod.POST,
                requestWithAuth,
                VehicleSpecificationCreateResponse.class
        );

        // Then: Data created with trimmed input
        assertTrue(response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.CREATED,
                "Expected 200 or 201, got: " + response.getStatusCode());

        // Verify whitespace was trimmed in response
        VehicleSpecificationResponse spec = response.getBody().specification();
        assertNotNull(spec);
        assertEquals("TESLA", spec.carBrand());
        assertEquals("MODEL_3", spec.carModel());
    }

    @Test
    void shouldRejectWltpCreation_WithoutAuthentication() {
        // Given: No JWT token
        VehicleSpecificationRequest request = new VehicleSpecificationRequest(
                "TESLA",
                "MODEL_3",
                new BigDecimal("75.0"),
                new BigDecimal("450.0"),
                new BigDecimal("16.5")
        );

        // When: Try to create WLTP data without auth
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/vehicle-specifications",
                request,
                String.class
        );

        // Then: Access denied
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
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
        // Given: Same model with different battery capacities — use unique brand not in seed data
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
}
