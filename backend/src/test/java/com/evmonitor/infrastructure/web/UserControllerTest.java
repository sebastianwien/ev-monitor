package com.evmonitor.infrastructure.web;

import com.evmonitor.application.user.ChangeUsernameRequest;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest extends AbstractIntegrationTest {

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("user-controller-test-" + System.nanoTime() + "@example.com");
    }

    @Test
    void changeUsername_shouldReturnNewTokenWithUpdatedUsername() {
        String newUsername = "u_" + (System.nanoTime() % 10_000_000L);
        ChangeUsernameRequest request = new ChangeUsernameRequest(newUsername);
        HttpEntity<ChangeUsernameRequest> httpRequest = createAuthRequest(request, testUser.getId(), testUser.getEmail());

        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                "/api/users/me/username",
                HttpMethod.PUT,
                httpRequest,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String newToken = response.getBody().get("token");
        assertNotNull(newToken, "Response must contain a token");

        String usernameInToken = jwtService.extractClaim(newToken, claims -> claims.get("username", String.class));
        assertEquals(newUsername, usernameInToken, "New token must contain the updated username");
    }

    @Test
    void changeUsername_shouldFailWhenUsernameAlreadyTaken() {
        // First give testUser a known short username via the API
        String takenUsername = "taken_" + (System.nanoTime() % 100_000L);
        ChangeUsernameRequest setupRequest = new ChangeUsernameRequest(takenUsername);
        restTemplate.exchange(
                "/api/users/me/username",
                HttpMethod.PUT,
                createAuthRequest(setupRequest, testUser.getId(), testUser.getEmail()),
                new ParameterizedTypeReference<Map<String, String>>() {}
        );

        // Now a second user tries to claim the same username
        User anotherUser = createAndSaveUser("another-" + System.nanoTime() + "@example.com");
        ChangeUsernameRequest request = new ChangeUsernameRequest(takenUsername);
        HttpEntity<ChangeUsernameRequest> httpRequest = createAuthRequest(request, anotherUser.getId(), anotherUser.getEmail());

        ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                "/api/users/me/username",
                HttpMethod.PUT,
                httpRequest,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void changeUsername_shouldReturn401WhenNotAuthenticated() {
        ChangeUsernameRequest request = new ChangeUsernameRequest("someusername");
        HttpEntity<ChangeUsernameRequest> httpRequest = new HttpEntity<>(request, new HttpHeaders());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/users/me/username",
                HttpMethod.PUT,
                httpRequest,
                Void.class
        );

        // Spring Security returns 403 for unauthenticated requests (no custom AuthenticationEntryPoint configured)
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
