-- V34: Full coin-log backfill for non-seed users.
-- Deletes all known-event coin entries and recomputes them correctly
-- with proper source_entity_id, first/subsequent amounts, and one-time guards.
-- WLTP contribution entries are preserved (cannot be reconstructed).

-- ============================================================
-- Step 1: Delete known-event coins for non-seed users
-- WLTP data contributions are excluded — they cannot be reconstructed.
-- ============================================================
DELETE FROM coin_log
WHERE user_id IN (SELECT id FROM app_user WHERE is_seed_data = false)
  AND action_description NOT LIKE 'WLTP data contribution%';

-- ============================================================
-- Step 2: CAR_CREATED — first car = 20 coins, subsequent = 5
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
WITH ranked AS (
    SELECT c.id,
           c.user_id,
           c.created_at,
           ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.created_at) AS rn
    FROM car c
    JOIN app_user u ON c.user_id = u.id
    WHERE u.is_seed_data = false
)
SELECT gen_random_uuid(),
       user_id,
       'ACHIEVEMENT_COIN',
       CASE WHEN rn = 1 THEN 20 ELSE 5 END,
       'Fahrzeug hinzugefügt',
       NULL,
       created_at
FROM ranked;

-- ============================================================
-- Step 3: MANUAL_LOG — USER_LOGGED — first = 25, subsequent = 5
-- source_entity_id = ev_log.id for deduction on deletion
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
WITH ranked AS (
    SELECT e.id      AS log_id,
           c.user_id,
           e.logged_at,
           ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY e.logged_at) AS rn
    FROM ev_log e
    JOIN car c ON e.car_id = c.id
    JOIN app_user u ON c.user_id = u.id
    WHERE u.is_seed_data = false
      AND e.data_source = 'USER_LOGGED'
)
SELECT gen_random_uuid(),
       user_id,
       'ACHIEVEMENT_COIN',
       CASE WHEN rn = 1 THEN 25 ELSE 5 END,
       'Ladevorgang erfasst',
       log_id,
       logged_at
FROM ranked;

-- ============================================================
-- Step 4: SPRITMONITOR_LOG — 2 coins per imported log
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       2,
       'Ladevorgang importiert (Sprit-Monitor)',
       e.id,
       e.logged_at
FROM ev_log e
JOIN car c ON e.car_id = c.id
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND e.data_source = 'SPRITMONITOR_IMPORT';

-- ============================================================
-- Step 5: SPRITMONITOR_CONNECTED — 50 coins, once per user
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       50,
       'Sprit-Monitor Import',
       NULL,
       MIN(e.logged_at)
FROM ev_log e
JOIN car c ON e.car_id = c.id
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND e.data_source = 'SPRITMONITOR_IMPORT'
GROUP BY c.user_id;

-- ============================================================
-- Step 6: TESLA_DAILY_LOG — 5 coins per TESLA_FLEET log
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       5,
       'Ladevorgang importiert (Tesla Sync)',
       e.id,
       e.logged_at
FROM ev_log e
JOIN car c ON e.car_id = c.id
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND e.data_source IN ('TESLA_FLEET', 'TESLA_HOME');

-- ============================================================
-- Step 7: TESLA_HISTORY_LOG — 2 coins per TESLA_LOGGER_IMPORT log
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       2,
       'Ladevorgang importiert (TeslaLogger)',
       e.id,
       e.logged_at
FROM ev_log e
JOIN car c ON e.car_id = c.id
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND e.data_source = 'TESLA_LOGGER_IMPORT';

-- ============================================================
-- Step 8: TESLA_LOGGER_CONNECTED — 20 coins, once per user
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       20,
       'TeslaLogger Import',
       NULL,
       MIN(e.logged_at)
FROM ev_log e
JOIN car c ON e.car_id = c.id
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND e.data_source = 'TESLA_LOGGER_IMPORT'
GROUP BY c.user_id;

-- ============================================================
-- Step 9: TESLA_CONNECTED — 50 coins, once per user (via tesla_connections)
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       tc.user_id,
       'ACHIEVEMENT_COIN',
       50,
       'Tesla Verbindung hinzugefügt',
       NULL,
       tc.created_at
FROM tesla_connections tc
JOIN app_user u ON tc.user_id = u.id
WHERE u.is_seed_data = false;

-- ============================================================
-- Step 10: IMAGE_UPLOADED — 15 coins, once per user (any car has image_path)
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       15,
       'Erstes Auto-Bild hochgeladen',
       NULL,
       MIN(c.updated_at)
FROM car c
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND c.image_path IS NOT NULL
GROUP BY c.user_id;

-- ============================================================
-- Step 11: IMAGE_PUBLIC — 10 coins, once per user (any car has image_public = true)
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       c.user_id,
       'ACHIEVEMENT_COIN',
       10,
       'Auto-Bild öffentlich geteilt',
       NULL,
       MIN(c.updated_at)
FROM car c
JOIN app_user u ON c.user_id = u.id
WHERE u.is_seed_data = false
  AND c.image_public = true
GROUP BY c.user_id;

-- ============================================================
-- Step 12: REFERRAL_INVITED — 100 coins per verified referral, max 20
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
WITH referrals AS (
    SELECT referred_by_user_id AS referrer_id,
           created_at,
           ROW_NUMBER() OVER (PARTITION BY referred_by_user_id ORDER BY created_at) AS rn
    FROM app_user
    WHERE is_seed_data = false
      AND referred_by_user_id IS NOT NULL
      AND email_verified = true
)
SELECT gen_random_uuid(),
       referrer_id,
       'ACHIEVEMENT_COIN',
       100,
       'Freund eingeladen',
       NULL,
       created_at
FROM referrals
WHERE rn <= 20;

-- ============================================================
-- Step 13: REFERRAL_WELCOME — 25 coins, once per referred user
-- ============================================================
INSERT INTO coin_log (id, user_id, coin_type, amount, action_description, source_entity_id, created_at)
SELECT gen_random_uuid(),
       u.id,
       'ACHIEVEMENT_COIN',
       25,
       'Willkommensbonus (eingeladen)',
       NULL,
       u.created_at
FROM app_user u
WHERE u.is_seed_data = false
  AND u.referred_by_user_id IS NOT NULL
  AND u.email_verified = true;
