package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.UserEntity;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID testUserId;
    private UserEntity testUserEntity;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        testUserEntity = new UserEntity(
                testUserId,
                "integration-test@example.com",
                "integrationuser",
                passwordEncoder.encode("password123"),
                AuthProvider.LOCAL,
                "USER",
                true,
                false,
                true,
                "TESTCODE1",
                null,
                now,
                now
        );

        testUserEntity = jpaUserRepository.save(testUserEntity);

        testUser = new User(
                testUserEntity.getId(),
                testUserEntity.getEmail(),
                testUserEntity.getUsername(),
                testUserEntity.getPasswordHash(),
                testUserEntity.getAuthProvider(),
                testUserEntity.getRole(),
                testUserEntity.isEmailVerified(),
                testUserEntity.isSeedData(),
                testUserEntity.isEmailNotificationsEnabled(),
                testUserEntity.isPremium(),
                testUserEntity.isReferralRewardGiven(),
                testUserEntity.getReferralCode(),
                testUserEntity.getReferredByUserId(),
                testUserEntity.getStripeCustomerId(),
                testUserEntity.getUtmSource(),
                testUserEntity.getUtmMedium(),
                testUserEntity.getUtmCampaign(),
                testUserEntity.getReferrerSource(),
                testUserEntity.getRegistrationLocale(),
                testUserEntity.getCountry(),
                testUserEntity.getCreatedAt(),
                testUserEntity.getUpdatedAt()
        );
    }

    @AfterEach
    void tearDown() {
        jpaUserRepository.deleteAll();
    }

    /**
     * Helper method to create Authentication with UserPrincipal (required by UserController)
     */
    private Authentication createAuthentication(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void getUserStats_shouldReturnUserStatistics() throws Exception {
        mockMvc.perform(get("/api/users/me/stats")
                        .with(authentication(createAuthentication(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredSince", notNullValue()))
                .andExpect(jsonPath("$.totalLogs", is(0)))
                .andExpect(jsonPath("$.totalKwh", is(0.0)))
                .andExpect(jsonPath("$.totalCostEur", is(0.0)));
    }

    @Test
    void changeEmail_shouldUpdateEmailSuccessfully() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("newEmail", "newemail@example.com");
        request.put("currentPassword", "password123");

        mockMvc.perform(put("/api/users/me/email")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify email was changed
        UserEntity updatedUser = jpaUserRepository.findById(testUserId).orElseThrow();
        assertEquals("newemail@example.com", updatedUser.getEmail());
        assertFalse(updatedUser.isEmailVerified()); // Should be unverified after change
    }

    @Test
    void changeEmail_shouldReturnBadRequestWhenEmailAlreadyExists() throws Exception {
        // Create another user with the target email
        UserEntity anotherUser = new UserEntity(
                UUID.randomUUID(),
                "existing@example.com",
                "existinguser",
                "hashedPassword",
                AuthProvider.LOCAL,
                "USER",
                true,
                false,
                true,
                "TESTCODE2",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        jpaUserRepository.save(anotherUser);

        Map<String, String> request = new HashMap<>();
        request.put("newEmail", "existing@example.com");
        request.put("currentPassword", "password123");

        mockMvc.perform(put("/api/users/me/email")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeEmail_shouldReturnBadRequestWhenPasswordIsWrong() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("newEmail", "newemail@example.com");
        request.put("currentPassword", "wrongPassword");

        mockMvc.perform(put("/api/users/me/email")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Aktuelles Passwort ist falsch")));

        // Email must not have changed
        UserEntity unchanged = jpaUserRepository.findById(testUserId).orElseThrow();
        assertEquals("integration-test@example.com", unchanged.getEmail());
    }

    @Test
    void changeEmail_shouldReturnBadRequestForOAuthUser() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        UserEntity oauthUserEntity = new UserEntity(
                UUID.randomUUID(),
                "oauth-user@example.com",
                "oauthuser",
                null,
                AuthProvider.GOOGLE,
                "USER",
                true,
                false,
                true,
                "OAUTHCODE1",
                null,
                now,
                now
        );
        oauthUserEntity = jpaUserRepository.save(oauthUserEntity);
        User oauthUser = new User(
                oauthUserEntity.getId(), oauthUserEntity.getEmail(), oauthUserEntity.getUsername(),
                null, oauthUserEntity.getAuthProvider(), oauthUserEntity.getRole(),
                true, false, true, false, false, oauthUserEntity.getReferralCode(), null, null, null, null, null, null, null, null,
                now, now
        );

        Map<String, String> request = new HashMap<>();
        request.put("newEmail", "newemail@example.com");
        request.put("currentPassword", "irrelevant");

        mockMvc.perform(put("/api/users/me/email")
                        .with(authentication(createAuthentication(oauthUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("lokal registrierte")));
    }

    @Test
    void changeUsername_shouldUpdateUsernameSuccessfully() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("newUsername", "newusername");

        mockMvc.perform(put("/api/users/me/username")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify username was changed
        UserEntity updatedUser = jpaUserRepository.findById(testUserId).orElseThrow();
        assertEquals("newusername", updatedUser.getUsername());
    }

    @Test
    void changeUsername_shouldReturnBadRequestForInvalidUsername() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("newUsername", "ab"); // Too short (min 3)

        mockMvc.perform(put("/api/users/me/username")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_shouldUpdatePasswordSuccessfully() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("currentPassword", "password123");
        request.put("newPassword", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify password was changed
        UserEntity updatedUser = jpaUserRepository.findById(testUserId).orElseThrow();
        assertTrue(passwordEncoder.matches("newPassword456", updatedUser.getPasswordHash()));
    }

    @Test
    void changePassword_shouldReturnBadRequestWhenCurrentPasswordIsWrong() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("currentPassword", "wrongPassword");
        request.put("newPassword", "newPassword456");

        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_shouldReturnBadRequestWhenNewPasswordIsTooShort() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("currentPassword", "password123");
        request.put("newPassword", "short"); // Too short (min 8)

        mockMvc.perform(put("/api/users/me/password")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportUserData_shouldReturnJsonFile() throws Exception {
        mockMvc.perform(get("/api/users/me/export")
                        .with(authentication(createAuthentication(testUser))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("ev-monitor-export")));
    }

    @Test
    void deleteAccount_shouldDeleteUserSuccessfully() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("password", "password123");

        mockMvc.perform(delete("/api/users/me")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify user was deleted
        assertFalse(jpaUserRepository.existsById(testUserId));
    }

    @Test
    void deleteAccount_shouldReturnBadRequestWhenPasswordIsWrong() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("password", "wrongPassword");

        mockMvc.perform(delete("/api/users/me")
                        .with(authentication(createAuthentication(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify user still exists
        assertTrue(jpaUserRepository.existsById(testUserId));
    }

    @Test
    void allEndpoints_shouldReturn401WhenNotAuthenticated() throws Exception {
        // Spring Security returns 403 Forbidden (not 401 Unauthorized) when no authentication is present
        mockMvc.perform(get("/api/users/me/stats"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/me/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/me/export"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
