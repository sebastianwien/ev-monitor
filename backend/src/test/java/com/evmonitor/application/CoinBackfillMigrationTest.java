package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the SQL logic from V34__backfill_coin_logs.sql against known test data.
 *
 * The test does NOT run Flyway. Instead it:
 *  1. Inserts a controlled set of users, cars, and ev_logs
 *  2. Runs the same SQL as V34 via JdbcTemplate (in a transaction that rolls back)
 *  3. Asserts exact coin counts and amounts
 *
 * This catches SQL bugs before they reach production.
 */
@Transactional
class CoinBackfillMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private User normalUser;
    private User seedUser;
    private Car car1;
    private Car car2;

    @BeforeEach
    void setUp() {
        // Non-seed user with a mix of logs
        normalUser = userRepository.save(TestDataBuilder.createTestUser("backfill-normal-" + System.nanoTime() + "@test.ev"));
        // Seed user — should NOT be touched by backfill
        seedUser = createSeedUser("backfill-seed-" + System.nanoTime() + "@test.ev");

        car1 = carRepository.save(TestDataBuilder.createTestCar(normalUser.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75.0)));
        car2 = carRepository.save(TestDataBuilder.createTestCar(normalUser.getId(), CarBrand.CarModel.MODEL_Y, BigDecimal.valueOf(82.0)));

        // Pre-insert a coin for the seed user to verify it survives the delete
        insertCoin(seedUser.getId(), 20, "Fahrzeug hinzugefügt", null);

        // Pre-insert a WLTP coin for normal user to verify it survives the delete
        insertCoin(normalUser.getId(), 50, "WLTP data contribution: TESLA MODEL_3 (75.0 kWh)", null);

        // Flush JPA writes so JdbcTemplate raw SQL can see them in the same transaction
        em.flush();
    }

    @Test
    void delete_removesOnlyKnownEvents_forNonSeedUsers() {
        insertCoin(normalUser.getId(), 25, "Ladevorgang erfasst", null);
        insertCoin(normalUser.getId(), 20, "Fahrzeug hinzugefügt", null);

        runBackfillDelete();

        List<CoinLog> remaining = coinLogRepository.findAllByUserId(normalUser.getId());
        // Only WLTP contribution survives
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getActionDescription()).startsWith("WLTP data contribution");
    }

    @Test
    void delete_doesNotTouchSeedUsers() {
        runBackfillDelete();

        List<CoinLog> seedCoins = coinLogRepository.findAllByUserId(seedUser.getId());
        assertThat(seedCoins).hasSize(1);
    }

    @Test
    void carCreated_awards20ForFirstCar_and5ForSubsequent() {
        runBackfillDelete();
        runBackfillCarCreated();

        List<CoinLog> coins = coinsForUser(normalUser.getId(), "Fahrzeug hinzugefügt");
        assertThat(coins).hasSize(2);

        // First car (earlier created_at) gets 20
        CoinLog first = coins.stream().filter(c -> c.getAmount() == 20).findFirst().orElseThrow();
        assertThat(first.getSourceEntityId()).isNull();

        // Second car gets 5
        CoinLog second = coins.stream().filter(c -> c.getAmount() == 5).findFirst().orElseThrow();
        assertThat(second.getSourceEntityId()).isNull();
    }

    @Test
    void manualLog_awards25ForFirst_and5ForSubsequent_withSourceEntityId() {
        UUID logId1 = insertEvLog(car1.getId(), "USER_LOGGED", LocalDateTime.now().minusDays(2));
        UUID logId2 = insertEvLog(car1.getId(), "USER_LOGGED", LocalDateTime.now().minusDays(1));
        UUID logId3 = insertEvLog(car2.getId(), "USER_LOGGED", LocalDateTime.now());

        runBackfillDelete();
        runBackfillManualLog();

        List<CoinLog> coins = coinsForUser(normalUser.getId(), "Ladevorgang erfasst");
        assertThat(coins).hasSize(3);

        CoinLog first = coins.stream().filter(c -> c.getAmount() == 25).findFirst().orElseThrow();
        assertThat(first.getSourceEntityId()).isEqualTo(logId1);

        List<CoinLog> subsequent = coins.stream().filter(c -> c.getAmount() == 5).toList();
        assertThat(subsequent).hasSize(2);
        assertThat(subsequent.stream().map(CoinLog::getSourceEntityId).toList())
                .containsExactlyInAnyOrder(logId2, logId3);
    }

    @Test
    void spritMonitorLog_awards2PerLog_withSourceEntityId() {
        UUID logId1 = insertEvLog(car1.getId(), "SPRITMONITOR_IMPORT", LocalDateTime.now().minusDays(2));
        UUID logId2 = insertEvLog(car1.getId(), "SPRITMONITOR_IMPORT", LocalDateTime.now().minusDays(1));

        runBackfillDelete();
        runBackfillSpritMonitorLog();

        List<CoinLog> coins = coinsForUser(normalUser.getId(), "Ladevorgang importiert (Sprit-Monitor)");
        assertThat(coins).hasSize(2);
        coins.forEach(c -> {
            assertThat(c.getAmount()).isEqualTo(2);
            assertThat(c.getSourceEntityId()).isIn(logId1, logId2);
        });
    }

    @Test
    void spritMonitorConnected_awards50Once() {
        insertEvLog(car1.getId(), "SPRITMONITOR_IMPORT", LocalDateTime.now().minusDays(2));
        insertEvLog(car1.getId(), "SPRITMONITOR_IMPORT", LocalDateTime.now().minusDays(1));

        runBackfillDelete();
        runBackfillSpritMonitorConnected();

        List<CoinLog> coins = coinsForUser(normalUser.getId(), "Sprit-Monitor Import");
        assertThat(coins).hasSize(1);
        assertThat(coins.get(0).getAmount()).isEqualTo(50);
        assertThat(coins.get(0).getSourceEntityId()).isNull();
    }

    @Test
    void imageCoin_awards15ForUpload_and10ForPublic() {
        jdbc.update("UPDATE car SET image_path = 'some/path.jpg', updated_at = NOW() WHERE id = ?", car1.getId());
        jdbc.update("UPDATE car SET image_public = true, updated_at = NOW() WHERE id = ?", car1.getId());

        runBackfillDelete();
        runBackfillImageCoins();

        List<CoinLog> upload = coinsForUser(normalUser.getId(), "Erstes Auto-Bild hochgeladen");
        assertThat(upload).hasSize(1);
        assertThat(upload.get(0).getAmount()).isEqualTo(15);

        List<CoinLog> pub = coinsForUser(normalUser.getId(), "Auto-Bild öffentlich geteilt");
        assertThat(pub).hasSize(1);
        assertThat(pub.get(0).getAmount()).isEqualTo(10);
    }

    // ── SQL execution helpers ─────────────────────────────────────────────────

    private void runBackfillDelete() {
        jdbc.update("""
                DELETE FROM coin_log
                WHERE user_id IN (SELECT id FROM app_user WHERE is_seed_data = false)
                  AND action_description NOT LIKE 'WLTP data contribution%'
                """);
    }

    private void runBackfillCarCreated() {
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                WITH ranked AS (
                    SELECT c.id, c.user_id, c.created_at,
                           ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.created_at) AS rn
                    FROM car c JOIN app_user u ON c.user_id = u.id WHERE u.is_seed_data = false
                )
                SELECT gen_random_uuid(), user_id, 'ACHIEVEMENT_COIN',
                       CASE WHEN rn = 1 THEN 20 ELSE 5 END, 'Fahrzeug hinzugefügt', NULL, created_at FROM ranked
                """);
    }

    private void runBackfillManualLog() {
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                WITH ranked AS (
                    SELECT e.id AS log_id, c.user_id, e.logged_at,
                           ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY e.logged_at) AS rn
                    FROM ev_log e JOIN car c ON e.car_id = c.id JOIN app_user u ON c.user_id = u.id
                    WHERE u.is_seed_data = false AND e.data_source = 'USER_LOGGED'
                )
                SELECT gen_random_uuid(), user_id, 'ACHIEVEMENT_COIN',
                       CASE WHEN rn = 1 THEN 25 ELSE 5 END, 'Ladevorgang erfasst', log_id, logged_at FROM ranked
                """);
    }

    private void runBackfillSpritMonitorLog() {
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                SELECT gen_random_uuid(), c.user_id, 'ACHIEVEMENT_COIN', 2,
                       'Ladevorgang importiert (Sprit-Monitor)', e.id, e.logged_at
                FROM ev_log e JOIN car c ON e.car_id = c.id JOIN app_user u ON c.user_id = u.id
                WHERE u.is_seed_data = false AND e.data_source = 'SPRITMONITOR_IMPORT'
                """);
    }

    private void runBackfillSpritMonitorConnected() {
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                SELECT gen_random_uuid(), c.user_id, 'ACHIEVEMENT_COIN', 50, 'Sprit-Monitor Import', NULL, MIN(e.logged_at)
                FROM ev_log e JOIN car c ON e.car_id = c.id JOIN app_user u ON c.user_id = u.id
                WHERE u.is_seed_data = false AND e.data_source = 'SPRITMONITOR_IMPORT'
                GROUP BY c.user_id
                """);
    }

    private void runBackfillImageCoins() {
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                SELECT gen_random_uuid(), c.user_id, 'ACHIEVEMENT_COIN', 15, 'Erstes Auto-Bild hochgeladen', NULL, MIN(c.updated_at)
                FROM car c JOIN app_user u ON c.user_id = u.id
                WHERE u.is_seed_data = false AND c.image_path IS NOT NULL
                GROUP BY c.user_id
                """);
        jdbc.update("""
                INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
                SELECT gen_random_uuid(), c.user_id, 'ACHIEVEMENT_COIN', 10, 'Auto-Bild öffentlich geteilt', NULL, MIN(c.updated_at)
                FROM car c JOIN app_user u ON c.user_id = u.id
                WHERE u.is_seed_data = false AND c.image_public = true
                GROUP BY c.user_id
                """);
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private List<CoinLog> coinsForUser(UUID userId, String description) {
        return coinLogRepository.findAllByUserId(userId).stream()
                .filter(c -> description.equals(c.getActionDescription()))
                .toList();
    }

    private void insertCoin(UUID userId, int amount, String description, UUID sourceEntityId) {
        jdbc.update(
                "INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at) VALUES (gen_random_uuid(), ?, 'ACHIEVEMENT_COIN', ?, ?, ?, NOW())",
                userId, amount, description, sourceEntityId
        );
    }

    private UUID insertEvLog(UUID carId, String dataSource, LocalDateTime loggedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ev_log (id, car_id, kwh_charged, cost_eur, logged_at, created_at, updated_at, data_source, include_in_statistics) VALUES (?, ?, 30.0, 10.0, ?, NOW(), NOW(), ?, true)",
                id, carId, loggedAt, dataSource
        );
        return id;
    }

    private User createSeedUser(String email) {
        String username = email.split("@")[0];
        User user = new User(
                UUID.randomUUID(), email, username,
                "$2a$10$N9qo8uLOickgx2ZMRZoMye7JU5qBvJqLzL/MQPVxqNGQqQfqzZ5bC",
                AuthProvider.LOCAL, "USER",
                true, true, true, false,
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
                null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        return userRepository.save(user);
    }
}
