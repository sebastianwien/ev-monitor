-- Remove remaining Hibernate-generated enum CHECK constraints
--
-- V3 already removed car_model_check, but other enum CHECK constraints remain.
-- These constraints can become out-of-sync when enum values are added in code,
-- causing production bugs.
--
-- This migration removes all remaining enum CHECK constraints:
-- - car_status_check (ACTIVE, INACTIVE)
-- - app_user_auth_provider_check (LOCAL, GOOGLE, APPLE, FACEBOOK)
-- - coin_log_coin_type_check (GREEN_COIN, DISTANCE_COIN, SOCIAL_COIN, etc.)
--
-- JPA @Enumerated(EnumType.STRING) validates at application level,
-- ensuring data integrity without brittle DB-level constraints.

-- Drop remaining car table enum CHECK constraint
ALTER TABLE car DROP CONSTRAINT IF EXISTS car_status_check;

-- Drop app_user table enum CHECK constraint
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_auth_provider_check;

-- Drop coin_log table enum CHECK constraint
ALTER TABLE coin_log DROP CONSTRAINT IF EXISTS coin_log_coin_type_check;
