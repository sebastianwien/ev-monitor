-- Add public charging fields to ev_log
ALTER TABLE ev_log ADD COLUMN is_public_charging BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ev_log ADD COLUMN cpo_name VARCHAR(100) NULL;

-- Widen geohash column to 7 characters for higher precision at public chargers
ALTER TABLE ev_log ALTER COLUMN geohash TYPE VARCHAR(7);
