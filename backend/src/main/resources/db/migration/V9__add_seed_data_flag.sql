-- V9: Add is_seed_data flag to app_user
-- Marks test/seed users so their data is excluded from public statistics.
-- All existing users get FALSE (real users). DevDataSeeder sets TRUE on creation.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS is_seed_data BOOLEAN NOT NULL DEFAULT FALSE;
