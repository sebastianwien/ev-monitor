-- Make charge_duration_minutes nullable to support Tesla Import (no duration data from API)
-- User can manually add duration later via Log Edit feature

ALTER TABLE ev_log
    ALTER COLUMN charge_duration_minutes DROP NOT NULL;

-- Add comment for clarity
COMMENT ON COLUMN ev_log.charge_duration_minutes IS 'Charge duration in minutes - nullable for auto-imported logs (e.g., TESLA_IMPORT) where duration is unknown';
