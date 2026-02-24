package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CoinBalanceResponse;
import com.evmonitor.application.CoinLogResponse;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for CoinLogController.
 * Tests coin balance and transaction history.
 *
 * BUSINESS CRITICAL: Coin system must accurately track rewards!
 */
class CoinLogControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUpTestData() {
        // Create test user with unique email
        testUser = createAndSaveUser("coin-test-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
    }

    @Test
    void shouldGetCoinBalance_EmptyForNewUser() {
        // Given: New user with no coins
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/balance
        ResponseEntity<CoinBalanceResponse> response = restTemplate.exchange(
                "/api/coins/balance",
                HttpMethod.GET,
                requestWithAuth,
                CoinBalanceResponse.class
        );

        // Then: Returns zero balance
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalCoins());
        assertTrue(response.getBody().coinsByType().isEmpty() ||
                response.getBody().coinsByType().values().stream().allMatch(v -> v == 0));
    }

    @Test
    void shouldGetCoinBalance_WithCoins() {
        // Given: User has earned coins
        CoinLog log1 = TestDataBuilder.createTestCoinLog(userId, CoinType.GREEN_COIN, 50, "First charge");
        CoinLog log2 = TestDataBuilder.createTestCoinLog(userId, CoinType.SOCIAL_COIN, 50, "WLTP contribution");
        CoinLog log3 = TestDataBuilder.createTestCoinLog(userId, CoinType.DISTANCE_COIN, 100, "Drove 1000km");
        coinLogRepository.save(log1);
        coinLogRepository.save(log2);
        coinLogRepository.save(log3);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/balance
        ResponseEntity<CoinBalanceResponse> response = restTemplate.exchange(
                "/api/coins/balance",
                HttpMethod.GET,
                requestWithAuth,
                CoinBalanceResponse.class
        );

        // Then: Returns correct balance
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().totalCoins()); // 50 + 50 + 100
        assertEquals(50, response.getBody().coinsByType().getOrDefault(CoinType.GREEN_COIN, 0));
        assertEquals(100, response.getBody().coinsByType().getOrDefault(CoinType.DISTANCE_COIN, 0));
        assertEquals(50, response.getBody().coinsByType().getOrDefault(CoinType.SOCIAL_COIN, 0));
    }

    @Test
    void shouldNotSeeOtherUsersCoins_SecurityCheck() {
        // Given: Another user with coins
        User otherUser = createAndSaveUser("other-coin-" + System.nanoTime() + "@example.com");
        CoinLog otherUserLog = TestDataBuilder.createTestCoinLog(
                otherUser.getId(), CoinType.GREEN_COIN, 1000, "Not your coins!");
        coinLogRepository.save(otherUserLog);

        // And: Current user has no coins
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/balance
        ResponseEntity<CoinBalanceResponse> response = restTemplate.exchange(
                "/api/coins/balance",
                HttpMethod.GET,
                requestWithAuth,
                CoinBalanceResponse.class
        );

        // Then: Should only see own balance (zero)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalCoins(), "User should not see other users' coins");
    }

    @Test
    void shouldGetCoinHistory_EmptyForNewUser() {
        // Given: New user with no transactions
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/logs
        ResponseEntity<List<CoinLogResponse>> response = restTemplate.exchange(
                "/api/coins/logs",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<CoinLogResponse>>() {}
        );

        // Then: Returns empty list
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void shouldGetCoinHistory_WithTransactions() {
        // Given: User has coin transactions
        CoinLog log1 = TestDataBuilder.createTestCoinLog(userId, CoinType.GREEN_COIN, 50, "First charge");
        CoinLog log2 = TestDataBuilder.createTestCoinLog(userId, CoinType.SOCIAL_COIN, 50, "WLTP contribution");
        coinLogRepository.save(log1);
        coinLogRepository.save(log2);

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/logs
        ResponseEntity<List<CoinLogResponse>> response = restTemplate.exchange(
                "/api/coins/logs",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<CoinLogResponse>>() {}
        );

        // Then: Returns all transactions
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        // Verify transaction details
        CoinLogResponse firstLog = response.getBody().get(0);
        assertNotNull(firstLog.id());
        assertNotNull(firstLog.coinType());
        assertTrue(firstLog.amount() == 50);
        assertNotNull(firstLog.actionDescription());
    }

    @Test
    void shouldNotSeeOtherUsersCoinHistory_SecurityCheck() {
        // Given: Another user with coin transactions
        User otherUser = createAndSaveUser("other-history-" + System.nanoTime() + "@example.com");
        CoinLog otherUserLog = TestDataBuilder.createTestCoinLog(
                otherUser.getId(), CoinType.GREEN_COIN, 1000, "Secret transaction");
        coinLogRepository.save(otherUserLog);

        // And: Current user has no transactions
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/logs
        ResponseEntity<List<CoinLogResponse>> response = restTemplate.exchange(
                "/api/coins/logs",
                HttpMethod.GET,
                requestWithAuth,
                new ParameterizedTypeReference<List<CoinLogResponse>>() {}
        );

        // Then: Should only see own history (empty)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), "User should not see other users' coin history");
    }

    @Test
    void shouldRejectCoinOperations_WithoutAuthentication() {
        // When: Try to get balance without auth
        ResponseEntity<String> balanceResponse = restTemplate.getForEntity(
                "/api/coins/balance",
                String.class
        );

        // Then: Access denied
        assertTrue(
                balanceResponse.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                balanceResponse.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + balanceResponse.getStatusCode()
        );

        // When: Try to get history without auth
        ResponseEntity<String> historyResponse = restTemplate.getForEntity(
                "/api/coins/logs",
                String.class
        );

        // Then: Access denied
        assertTrue(
                historyResponse.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                historyResponse.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + historyResponse.getStatusCode()
        );
    }

    @Test
    void shouldHandleMultipleCoinTypes() {
        // Given: User has earned different types of coins
        coinLogRepository.save(TestDataBuilder.createTestCoinLog(userId, CoinType.GREEN_COIN, 25, "Eco driving"));
        coinLogRepository.save(TestDataBuilder.createTestCoinLog(userId, CoinType.DISTANCE_COIN, 75, "Road trip"));
        coinLogRepository.save(TestDataBuilder.createTestCoinLog(userId, CoinType.SOCIAL_COIN, 50, "Shared data"));
        coinLogRepository.save(TestDataBuilder.createTestCoinLog(userId, CoinType.STREAK_COIN, 100, "7-day streak"));
        coinLogRepository.save(TestDataBuilder.createTestCoinLog(userId, CoinType.ACHIEVEMENT_COIN, 200, "First 1000km"));

        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, testUser.getEmail());

        // When: GET /api/coins/balance
        ResponseEntity<CoinBalanceResponse> response = restTemplate.exchange(
                "/api/coins/balance",
                HttpMethod.GET,
                requestWithAuth,
                CoinBalanceResponse.class
        );

        // Then: All coin types are counted correctly
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(450, response.getBody().totalCoins()); // 25+75+50+100+200
        assertEquals(25, response.getBody().coinsByType().getOrDefault(CoinType.GREEN_COIN, 0));
        assertEquals(75, response.getBody().coinsByType().getOrDefault(CoinType.DISTANCE_COIN, 0));
        assertEquals(50, response.getBody().coinsByType().getOrDefault(CoinType.SOCIAL_COIN, 0));
        assertEquals(100, response.getBody().coinsByType().getOrDefault(CoinType.STREAK_COIN, 0));
        assertEquals(200, response.getBody().coinsByType().getOrDefault(CoinType.ACHIEVEMENT_COIN, 0));
    }
}
