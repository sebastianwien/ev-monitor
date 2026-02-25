-- Add username support to app_user table
-- Phase 2: User Management Enhancement

-- Step 1: Add username column (nullable first for data migration)
ALTER TABLE app_user ADD COLUMN username VARCHAR(50);

-- Step 2: Data migration - Use email as username for existing users
-- This ensures no NULL values and no duplicates (email is already unique)
UPDATE app_user SET username = email WHERE username IS NULL;

-- Step 3: Make username NOT NULL and add unique constraint
ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX idx_app_user_username ON app_user(username);

COMMENT ON COLUMN app_user.username IS 'Unique username (3-20 alphanumeric + underscore). Existing users have email as username.';
