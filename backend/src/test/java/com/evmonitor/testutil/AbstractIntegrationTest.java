package com.evmonitor.testutil;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

/**
 * Base class for integration tests.
 * Provides common setup and utility methods for testing with Spring Boot context.
 *
 * Key Features:
 * - Full Spring Boot context with RANDOM_PORT
 * - TestRestTemplate for HTTP requests
 * - All repositories available
 * - @Transactional for automatic rollback
 * - Helper methods for authenticated requests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CarRepository carRepository;

    @Autowired
    protected EvLogRepository evLogRepository;

    @Autowired
    protected VehicleSpecificationRepository vehicleSpecificationRepository;

    @Autowired
    protected CoinLogRepository coinLogRepository;

    @Autowired
    protected JwtService jwtService;

    /**
     * Create HTTP headers with JWT Bearer token for authenticated requests.
     */
    protected HttpHeaders createAuthHeaders(UUID userId, String email) {
        String token = JwtTestUtils.generateValidToken(userId, email, jwtService);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * Create HTTP entity with body and auth headers.
     */
    protected <T> HttpEntity<T> createAuthRequest(T body, UUID userId, String email) {
        return new HttpEntity<>(body, createAuthHeaders(userId, email));
    }

    /**
     * Create HTTP entity with only auth headers (for GET requests).
     */
    protected HttpEntity<Void> createAuthRequest(UUID userId, String email) {
        return new HttpEntity<>(createAuthHeaders(userId, email));
    }

    /**
     * Create a test user and save to database.
     */
    protected User createAndSaveUser(String email) {
        User user = TestDataBuilder.createTestUser(email);
        return userRepository.save(user);
    }

    /**
     * Create a test car for a user and save to database.
     */
    protected Car createAndSaveCar(UUID userId, CarBrand.CarModel model) {
        Car car = TestDataBuilder.createTestCar(userId, model, java.math.BigDecimal.valueOf(75.0));
        return carRepository.save(car);
    }
}
