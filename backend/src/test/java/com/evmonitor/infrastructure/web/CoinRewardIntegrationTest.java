package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarCreateResponse;
import com.evmonitor.application.CarRequest;
import com.evmonitor.application.CoinBalanceResponse;
import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.EvLog;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the coin reward system.
 *
 * Verifies that coins are correctly awarded for:
 * - Creating the first car (20 ACHIEVEMENT_COIN)
 * - Creating each subsequent car (5 ACHIEVEMENT_COIN)
 * - Logging the first charging session (25 ACHIEVEMENT_COIN)
 * - Logging each subsequent charging session (5 ACHIEVEMENT_COIN)
 * - Using OCR to fill in charging data (+2 ACHIEVEMENT_COIN bonus)
 *
 * Also verifies that rewards are reflected in the coin balance.
 */
class CoinRewardIntegrationTest extends AbstractIntegrationTest {

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUpTestData() {
        testUser = createAndSaveUser("coin-reward-" + System.nanoTime() + "@example.com");
        userId = testUser.getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Car creation rewards
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldAward20Coins_ForFirstCar() {
        // When: User creates their first car
        ResponseEntity<CarCreateResponse> response = createCarViaApi(CarBrand.CarModel.MODEL_3);

        // Then: 20 coins are awarded
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(20, response.getBody().coinsAwarded(),
                "First car must award exactly 20 coins");
    }

    @Test
    void shouldAward5Coins_ForEachSubsequentCar() {
        // Given: User already has one car
        createCarViaApi(CarBrand.CarModel.MODEL_3);

        // When: User creates a second car
        ResponseEntity<CarCreateResponse> secondResponse = createCarViaApi(CarBrand.CarModel.I4);

        // Then: Only 5 coins for the second car
        assertEquals(HttpStatus.CREATED, secondResponse.getStatusCode());
        assertNotNull(secondResponse.getBody());
        assertEquals(5, secondResponse.getBody().coinsAwarded(),
                "Second car must award exactly 5 coins");

        // And: Third car also gives 5
        ResponseEntity<CarCreateResponse> thirdResponse = createCarViaApi(CarBrand.CarModel.IONIQ_5);
        assertNotNull(thirdResponse.getBody());
        assertEquals(5, thirdResponse.getBody().coinsAwarded(),
                "Third car must award exactly 5 coins");
    }

    @Test
    void shouldPersistCoinLogEntry_WhenCarCreated() {
        // When: User creates their first car
        createCarViaApi(CarBrand.CarModel.MODEL_3);

        // Then: A coin log entry exists in the database
        List<CoinLog> logs = coinLogRepository.findAllByUserId(userId);
        assertEquals(1, logs.size());
        CoinLog entry = logs.get(0);
        assertEquals(CoinType.ACHIEVEMENT_COIN, entry.getCoinType());
        assertEquals(20, entry.getAmount());
        assertEquals("Fahrzeug hinzugefügt", entry.getActionDescription());
        assertEquals(userId, entry.getUserId());
    }

    @Test
    void shouldUpdateBalance_AfterCarCreation() {
        // Given: User has no coins
        assertEquals(0, fetchBalance());

        // When: User creates first car (20 coins) then second car (5 coins)
        createCarViaApi(CarBrand.CarModel.MODEL_3);
        createCarViaApi(CarBrand.CarModel.I4);

        // Then: Balance is 25 coins total
        assertEquals(25, fetchBalance(), "Balance must equal sum of all coin awards");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Charging log rewards
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldAward25Coins_ForFirstChargingLog() {
        // Given: User has a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        // When: User logs their first charging session
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), false);

        // Then: 25 coins awarded
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(25, response.getBody().coinsAwarded(),
                "First charging log must award exactly 25 coins");
    }

    @Test
    void shouldAward5Coins_ForEachSubsequentChargingLog() {
        // Given: User has a car and an existing log
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        createLogViaApi(car.getId(), false); // first log = 25 coins

        // When: User logs a second charging session
        ResponseEntity<EvLogCreateResponse> secondResponse = createLogViaApi(car.getId(), false);

        // Then: Only 5 coins for the second log
        assertNotNull(secondResponse.getBody());
        assertEquals(5, secondResponse.getBody().coinsAwarded(),
                "Second charging log must award exactly 5 coins");

        // And: Third log also gives 5
        ResponseEntity<EvLogCreateResponse> thirdResponse = createLogViaApi(car.getId(), false);
        assertNotNull(thirdResponse.getBody());
        assertEquals(5, thirdResponse.getBody().coinsAwarded(),
                "Third charging log must award exactly 5 coins");
    }

    @Test
    void shouldAward2BonusCoins_WhenOcrUsed_OnFirstLog() {
        // Given: User has a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        // When: User logs their first charging session WITH OCR
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), true);

        // Then: 25 base coins + 2 OCR bonus = 27 total
        assertNotNull(response.getBody());
        assertEquals(27, response.getBody().coinsAwarded(),
                "First log with OCR must award 25 + 2 = 27 coins");
    }

    @Test
    void shouldAward27Coins_WhenOcrUsed_AsFirstOcrLog_AfterNonOcrLog() {
        // Given: User already has a non-OCR log (25 coins)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        createLogViaApi(car.getId(), false); // first log, no OCR → 25 coins

        // When: Next log uses OCR — but it's the FIRST OCR log ever → MANUAL_LOG_FIRST_OCR
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), true);

        // Then: OCR events are independent from non-OCR: first OCR log always gets 27
        assertNotNull(response.getBody());
        assertEquals(27, response.getBody().coinsAwarded(),
                "First log with OCR must award 27 coins regardless of prior non-OCR logs");
    }

    @Test
    void shouldAward7Coins_WhenOcrUsed_OnSubsequentOcrLog() {
        // Given: User already has an OCR log (27 coins)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        createLogViaApi(car.getId(), true); // first OCR log → 27 coins

        // When: Second log also uses OCR → MANUAL_LOG_OCR
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), true);

        // Then: Subsequent OCR logs get 7 coins
        assertNotNull(response.getBody());
        assertEquals(7, response.getBody().coinsAwarded(),
                "Subsequent OCR logs must award 7 coins");
    }

    @Test
    void shouldNotAwardOcrBonus_WhenOcrUsedIsFalse() {
        // Given: User has a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        // When: First log without OCR
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), false);

        // Then: Exactly 25 (no bonus)
        assertNotNull(response.getBody());
        assertEquals(25, response.getBody().coinsAwarded(),
                "Log without OCR must not include the +2 bonus");
    }

    @Test
    void shouldPersistCoinLogEntry_WhenChargingLogCreated() {
        // Given: User has a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        // When: User logs a charging session
        createLogViaApi(car.getId(), false);

        // Then: A coin log entry exists in the database
        List<CoinLog> logs = coinLogRepository.findAllByUserId(userId);
        assertEquals(1, logs.size());
        CoinLog entry = logs.get(0);
        assertEquals(CoinType.ACHIEVEMENT_COIN, entry.getCoinType());
        assertEquals(25, entry.getAmount());
        assertEquals("Ladevorgang erfasst", entry.getActionDescription());
        assertEquals(userId, entry.getUserId());
    }

    @Test
    void shouldCountLogsAcrossAllCars_ForFirstLogDetection() {
        // Given: User has two cars
        Car car1 = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        Car car2 = createAndSaveCar(userId, CarBrand.CarModel.I4);

        // When: First log on car1 (25 coins), then first log on car2
        ResponseEntity<EvLogCreateResponse> firstLog = createLogViaApi(car1.getId(), false);
        ResponseEntity<EvLogCreateResponse> secondLog = createLogViaApi(car2.getId(), false);

        // Then: car2 log is NOT treated as a "first log" – counts total logs across all cars
        assertNotNull(firstLog.getBody());
        assertEquals(25, firstLog.getBody().coinsAwarded(), "First log ever must award 25 coins");

        assertNotNull(secondLog.getBody());
        assertEquals(5, secondLog.getBody().coinsAwarded(),
                "Second log (on different car) must award only 5 coins");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sourceEntityId — coin log is linked to the EvLog that triggered it
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldPersistSourceEntityId_WhenChargingLogCreated() {
        // Given: User has a car
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);

        // When: User logs a charging session
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), false);
        assertNotNull(response.getBody());
        UUID logId = response.getBody().log().id();

        // Then: The coin log entry links back to the EvLog via sourceEntityId
        List<CoinLog> logs = coinLogRepository.findAllByUserId(userId);
        assertEquals(1, logs.size());
        assertEquals(logId, logs.get(0).getSourceEntityId(),
                "Coin log must have sourceEntityId pointing to the created EvLog");
    }

    @Test
    void sumCoinsForSourceEntity_returnsCorrectSum_FromDatabase() {
        // Given: User has a car and creates a log (awards 25 coins)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        ResponseEntity<EvLogCreateResponse> response = createLogViaApi(car.getId(), false);
        assertNotNull(response.getBody());
        UUID logId = response.getBody().log().id();

        // When: sum is queried directly via repository
        int sum = coinLogRepository.sumCoinsForSourceEntity(logId);

        // Then: sum matches what was awarded
        assertEquals(25, sum, "sumCoinsForSourceEntity must return sum of all coins linked to this log");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Coin deduction on log deletion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldDeductCoins_WhenChargingLogDeleted() {
        // Given: User creates a first log (25 coins awarded)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        ResponseEntity<EvLogCreateResponse> logResponse = createLogViaApi(car.getId(), false);
        assertNotNull(logResponse.getBody());
        UUID logId = logResponse.getBody().log().id();
        assertEquals(25, fetchBalance(), "Pre-condition: 25 coins before deletion");

        // When: Log is deleted via API
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/logs/" + logId, HttpMethod.DELETE, auth, Void.class);

        // Then: Balance is back to 0 (25 awarded, 25 deducted)
        assertEquals(0, fetchBalance(),
                "Deleting a log must deduct the coins that were awarded for it");
    }

    @Test
    void shouldDeductCorrectAmount_ForSubsequentLogDeletion() {
        // Given: First log (25 coins) + second log (5 coins)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        createLogViaApi(car.getId(), false); // first: 25 coins
        ResponseEntity<EvLogCreateResponse> secondResponse = createLogViaApi(car.getId(), false);
        assertNotNull(secondResponse.getBody());
        UUID secondLogId = secondResponse.getBody().log().id();
        assertEquals(30, fetchBalance(), "Pre-condition: 30 coins (25+5) before deletion");

        // When: Second log is deleted
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/logs/" + secondLogId, HttpMethod.DELETE, auth, Void.class);

        // Then: 5 coins deducted, 25 remain
        assertEquals(25, fetchBalance(),
                "Deleting the second log must deduct exactly 5 coins");
    }

    @Test
    void shouldCreateNegativeCoinLogEntry_WhenLogDeleted() {
        // Given: User creates a first log (25 coins)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        ResponseEntity<EvLogCreateResponse> logResponse = createLogViaApi(car.getId(), false);
        assertNotNull(logResponse.getBody());
        UUID logId = logResponse.getBody().log().id();

        // When: Log is deleted
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/logs/" + logId, HttpMethod.DELETE, auth, Void.class);

        // Then: Coin history contains both the award (+25) and the deduction (-25)
        List<CoinLog> allLogs = coinLogRepository.findAllByUserId(userId);
        assertEquals(2, allLogs.size(), "Coin history must have award + deduction entry");
        int totalAmount = allLogs.stream().mapToInt(CoinLog::getAmount).sum();
        assertEquals(0, totalAmount, "Net coins after award + deduction must be 0");

        // And the deduction entry has negative amount and same sourceEntityId
        CoinLog deduction = allLogs.stream().filter(l -> l.getAmount() < 0).findFirst().orElseThrow();
        assertEquals(-25, deduction.getAmount());
        assertEquals("Ladevorgang gelöscht", deduction.getActionDescription());
        assertEquals(logId, deduction.getSourceEntityId());
    }

    @Test
    void shouldNotCreateDeductionEntry_WhenLogHasNoCoinHistory() {
        // Given: A log that was saved directly (bypassing the service, simulating pre-feature data)
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        // Save log directly without going through the API (= no coins awarded = no sourceEntityId)
        EvLog rawLog = EvLog.createNew(
                car.getId(), new BigDecimal("30"), new BigDecimal("7.50"),
                45, null, 10000, null, 80, LocalDateTime.now().minusDays(1),
                ChargingType.AC, null, null);
        EvLog saved = evLogRepository.save(rawLog);

        // When: Log is deleted via API
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/logs/" + saved.getId(), HttpMethod.DELETE, auth, Void.class);

        // Then: No deduction entry — coin_log remains empty
        List<CoinLog> logs = coinLogRepository.findAllByUserId(userId);
        assertEquals(0, logs.size(),
                "No deduction entry must be created when log had no coins awarded");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security: delete-and-recreate farming prevention
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldNotAward20CoinsAgain_AfterDeletingAndReCreatingCar() {
        // Given: User creates and then deletes their first car
        ResponseEntity<CarCreateResponse> first = createCarViaApi(CarBrand.CarModel.MODEL_3);
        assertNotNull(first.getBody());
        assertEquals(20, first.getBody().coinsAwarded(), "First car must award 20 coins");

        UUID firstCarId = first.getBody().car().id();
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/cars/" + firstCarId, HttpMethod.DELETE, auth, Void.class);

        // When: User creates a new car (trying to re-farm the 20-coin bonus)
        ResponseEntity<CarCreateResponse> second = createCarViaApi(CarBrand.CarModel.I4);

        // Then: Only 5 coins — coin history is the source of truth, not current car count
        assertNotNull(second.getBody());
        assertEquals(5, second.getBody().coinsAwarded(),
                "Re-creating a car after deletion must NOT re-award the 20-coin first-time bonus");
    }

    @Test
    void shouldNotAward25CoinsAgain_AfterDeletingAndReCreatingLog() {
        // Given: User creates and then deletes their first charging log
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        ResponseEntity<EvLogCreateResponse> first = createLogViaApi(car.getId(), false);
        assertNotNull(first.getBody());
        assertEquals(25, first.getBody().coinsAwarded(), "First log must award 25 coins");

        UUID firstLogId = first.getBody().log().id();
        HttpEntity<Void> auth = createAuthRequest(userId, testUser.getEmail());
        restTemplate.exchange("/api/logs/" + firstLogId, HttpMethod.DELETE, auth, Void.class);

        // When: User creates a new log (trying to re-farm the 25-coin bonus)
        ResponseEntity<EvLogCreateResponse> second = createLogViaApi(car.getId(), false);

        // Then: Only 5 coins — coin history is the source of truth, not current log count
        assertNotNull(second.getBody());
        assertEquals(5, second.getBody().coinsAwarded(),
                "Re-creating a log after deletion must NOT re-award the 25-coin first-time bonus");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Combined balance tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldAccumulateBalance_AcrossAllActions() {
        // Given: User starts with zero coins
        assertEquals(0, fetchBalance());

        // When: User does multiple actions
        // 1. First car: +20
        Car car = createAndSaveCar(userId, CarBrand.CarModel.MODEL_3);
        createCarViaApi(CarBrand.CarModel.MODEL_3); // use API to trigger coins
        int expected = 20;

        // Actually, createAndSaveCar bypasses the service – only the API awards coins.
        // So let's measure only API-triggered actions:

        // Reset: fresh user
        User freshUser = createAndSaveUser("fresh-" + System.nanoTime() + "@example.com");
        UUID freshId = freshUser.getId();

        // 1. Create first car via API: +20
        CarCreateResponse carResult = createCarViaApiForUser(freshId, freshUser.getEmail(),
                CarBrand.CarModel.MODEL_3).getBody();
        assertNotNull(carResult);
        Car freshCar = carRepository.findById(carResult.car().id()).orElseThrow();

        // 2. Create second car via API: +5
        createCarViaApiForUser(freshId, freshUser.getEmail(), CarBrand.CarModel.I4);

        // 3. First charging log via API: +25
        createLogViaApiForUser(freshId, freshUser.getEmail(), freshCar.getId(), false);

        // 4. Second charging log via API: +5
        createLogViaApiForUser(freshId, freshUser.getEmail(), freshCar.getId(), false);

        // Then: Total balance = 20 + 5 + 25 + 5 = 55
        int balance = fetchBalanceForUser(freshId, freshUser.getEmail());
        assertEquals(55, balance, "Total balance must equal sum of all awarded coins");
    }

    @Test
    void shouldIsolateCoins_BetweenUsers() {
        // Given: Two users
        User user2 = createAndSaveUser("isolated-" + System.nanoTime() + "@example.com");

        // When: user1 creates a car
        createCarViaApi(CarBrand.CarModel.MODEL_3);

        // Then: user2 sees zero coins
        int user2Balance = fetchBalanceForUser(user2.getId(), user2.getEmail());
        assertEquals(0, user2Balance, "User2 must not see user1's coin rewards");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<CarCreateResponse> createCarViaApi(CarBrand.CarModel model) {
        return createCarViaApiForUser(userId, testUser.getEmail(), model);
    }

    private ResponseEntity<CarCreateResponse> createCarViaApiForUser(
            UUID uid, String email, CarBrand.CarModel model) {
        CarRequest request = new CarRequest(
                model,
                2024,
                "TEST-" + System.nanoTime(),
                "Standard",
                new BigDecimal("75.0"),
                new BigDecimal("150.0"),
                null,
                false
        );
        HttpEntity<CarRequest> entity = createAuthRequest(request, uid, email);
        return restTemplate.exchange("/api/cars", HttpMethod.POST, entity, CarCreateResponse.class);
    }

    private ResponseEntity<EvLogCreateResponse> createLogViaApi(UUID carId, boolean ocrUsed) {
        return createLogViaApiForUser(userId, testUser.getEmail(), carId, ocrUsed);
    }

    private ResponseEntity<EvLogCreateResponse> createLogViaApiForUser(
            UUID uid, String email, UUID carId, boolean ocrUsed) {
        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("50.0"),
                new BigDecimal("12.50"),
                60,
                null, null,   // no GPS
                50000, null,   // odometerKm (required), no max power
                80, // socAfterChargePercent (required)
                LocalDateTime.now(),
                ocrUsed,      // OCR flag
                null,         // chargingType
                null, null    // routeType, tireType
        );
        HttpEntity<EvLogRequest> entity = createAuthRequest(request, uid, email);
        return restTemplate.exchange("/api/logs", HttpMethod.POST, entity, EvLogCreateResponse.class);
    }

    private int fetchBalance() {
        return fetchBalanceForUser(userId, testUser.getEmail());
    }

    private int fetchBalanceForUser(UUID uid, String email) {
        HttpEntity<Void> entity = createAuthRequest(uid, email);
        ResponseEntity<CoinBalanceResponse> response = restTemplate.exchange(
                "/api/coins/balance", HttpMethod.GET, entity, CoinBalanceResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().totalCoins();
    }
}
