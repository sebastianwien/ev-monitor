package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarCreateResponse;
import com.evmonitor.application.CarRequest;
import com.evmonitor.application.CarResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for CarController.
 * Tests CRUD operations and ownership validation.
 *
 * SECURITY CRITICAL: Users must only access/modify their own cars!
 */
class CarControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUpTestData() {
        // Create test user with unique email
        testUser = createAndSaveUser("car-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
    }

    @Test
    void shouldCreateCar_Success() {
        // Given: Car creation request
        CarRequest request = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2024,
                "TEST-123",
                "Long Range",
                new BigDecimal("79.0"),
                new BigDecimal("350.0"),
                null,
                false
        );

        HttpEntity<CarRequest> requestWithAuth = createAuthRequest(request, userId, testUser.getEmail());

        // When: POST /api/cars
        ResponseEntity<CarCreateResponse> response = restTemplate.exchange(
                "/api/cars",
                HttpMethod.POST,
                requestWithAuth,
                CarCreateResponse.class
        );

        // Then: Car created successfully with coins awarded
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(CarBrand.CarModel.MODEL_3, response.getBody().car().model());
        assertEquals(2024, response.getBody().car().year());
        assertEquals("TEST-123", response.getBody().car().licensePlate());
        assertEquals(20, response.getBody().coinsAwarded(), "First car must award 20 coins");

        // Verify car is in database
        Car savedCar = carRepository.findById(response.getBody().car().id()).orElseThrow();
        assertEquals(userId, savedCar.getUserId(), "Car must be owned by user");
    }

    @Test
    void shouldGetAllCarsForUser() {
        // Given: User has 2 cars
        Car car1 = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        Car car2 = createAndSaveCar(userId, CarBrand.CarModel.I4);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/cars
        ResponseEntity<List<CarResponse>> response = restTemplate.exchange(
                "/api/cars",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<CarResponse>>() {}
        );

        // Then: Returns all user's cars
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldNotSeeOtherUsersCars_SecurityCheck() {
        // Given: Another user with their own car
        User otherUser = createAndSaveUser("other-car-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.IONIQ_5);

        // And: Current user has no cars
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/cars
        ResponseEntity<List<CarResponse>> response = restTemplate.exchange(
                "/api/cars",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<CarResponse>>() {}
        );

        // Then: User should NOT see other user's cars
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), "User should not see other users' cars");
    }

    @Test
    void shouldUpdateCar_OnlyIfOwner() {
        // Given: User owns a car
        Car existingCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        CarRequest updateRequest = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2025,
                "NEW-456",
                "Performance",
                new BigDecimal("82.0"),
                new BigDecimal("450.0"),
                null,
                false
        );

        HttpEntity<CarRequest> requestWithAuth = createAuthRequest(updateRequest, userId, testUser.getEmail());

        // When: PUT /api/cars/{id}
        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + existingCar.getId(),
                HttpMethod.PUT,
                requestWithAuth,
                CarResponse.class
        );

        // Then: Car updated successfully
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2025, response.getBody().year());
        assertEquals("NEW-456", response.getBody().licensePlate());
    }

    @Test
    void shouldRejectUpdate_IfNotOwner() {
        // Given: Another user's car
        User otherUser = createAndSaveUser("other-update-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.I4);

        CarRequest updateRequest = new CarRequest(
                CarBrand.CarModel.I4,
                2025,
                "HACKED-123",
                "Stolen",
                new BigDecimal("80.0"),
                new BigDecimal("400.0"),
                null,
                false
        );

        HttpEntity<CarRequest> requestWithAuth = createAuthRequest(updateRequest, userId, testUser.getEmail());

        // When: Try to update other user's car
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + otherUserCar.getId(),
                HttpMethod.PUT,
                requestWithAuth,
                String.class
        );

        // Then: Should be rejected
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void shouldDeleteCar_OnlyIfOwner() {
        // Given: User owns a car
        Car ownedCar = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        UUID carId = ownedCar.getId();

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: DELETE /api/cars/{id}
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/cars/" + carId,
                HttpMethod.DELETE,
                requestWithAuth,
                Void.class
        );

        // Then: Car deleted successfully
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify car is deleted from database
        assertFalse(carRepository.findById(carId).isPresent());
    }

    @Test
    void shouldRejectDelete_IfNotOwner() {
        // Given: Another user's car
        User otherUser = createAndSaveUser("other-delete-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.I4);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: Try to delete other user's car
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + otherUserCar.getId(),
                HttpMethod.DELETE,
                requestWithAuth,
                String.class
        );

        // Then: Should be rejected
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        // Verify car still exists
        assertTrue(carRepository.findById(otherUserCar.getId()).isPresent());
    }

    @Test
    void shouldRejectCarOperations_WithoutAuthentication() {
        // Given: No JWT token
        CarRequest request = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2024,
                "TEST-123",
                "Standard",
                new BigDecimal("75.0"),
                new BigDecimal("350.0"),
                null,
                false
        );

        // When: Try to create car without auth
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/cars",
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
    void shouldActivateCar_Success() {
        // Given: User owns a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: PUT /api/cars/{id}/activate
        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + car.getId() + "/activate",
                HttpMethod.PUT,
                requestWithAuth,
                CarResponse.class
        );

        // Then: Car is now primary
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isPrimary(), "Car must be primary after activation");

        // Verify persisted in database
        Car saved = carRepository.findById(car.getId()).orElseThrow();
        assertTrue(saved.isPrimary(), "isPrimary must be persisted in DB");
    }

    @Test
    void shouldActivateCar_DeactivatesOtherCars() {
        // Given: User has two cars; activate car2 after car1
        Car car1 = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        Car car2 = createAndSaveCar(userId, CarBrand.CarModel.I4);
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // First activate car1
        restTemplate.exchange(
                "/api/cars/" + car1.getId() + "/activate",
                HttpMethod.PUT,
                requestWithAuth,
                CarResponse.class
        );

        // Now activate car2
        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + car2.getId() + "/activate",
                HttpMethod.PUT,
                requestWithAuth,
                CarResponse.class
        );

        // Then: car2 is primary
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isPrimary(), "car2 must be primary");

        // And car1 is no longer primary
        Car updatedCar1 = carRepository.findById(car1.getId()).orElseThrow();
        assertFalse(updatedCar1.isPrimary(), "car1 must no longer be primary");
    }

    @Test
    void shouldRejectActivateCar_IfNotOwner() {
        // Given: Another user's car
        User otherUser = createAndSaveUser("other-activate-" + System.nanoTime() + "@example.com");
        Car otherUserCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.IONIQ_5);
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: Try to activate another user's car
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + otherUserCar.getId() + "/activate",
                HttpMethod.PUT,
                requestWithAuth,
                String.class
        );

        // Then: Rejected
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        // Verify car was not changed
        Car notChanged = carRepository.findById(otherUserCar.getId()).orElseThrow();
        assertFalse(notChanged.isPrimary(), "Other user's car must not be modified");
    }

    @Test
    void shouldRejectActivateCar_WithoutAuthentication() {
        // Given: No JWT token, some car ID
        UUID randomCarId = UUID.randomUUID();

        // When: Try to activate without auth
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + randomCarId + "/activate",
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                String.class
        );

        // Then: Access denied
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }
}
