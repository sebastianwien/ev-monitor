ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_premium boolean NOT NULL DEFAULT false;
