ALTER TABLE app_user
    ADD COLUMN trial_used BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: alle aktuell aktiven Premium-User haben ihren Trial bereits verbraucht
UPDATE app_user
SET trial_used = TRUE
WHERE is_premium = TRUE;
