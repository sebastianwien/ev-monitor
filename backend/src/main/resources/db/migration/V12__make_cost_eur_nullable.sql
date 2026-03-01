-- Make cost_eur nullable to support Tesla Import (no cost data from API)
-- User can manually add costs later via Log Edit feature

ALTER TABLE ev_log
    ALTER COLUMN cost_eur DROP NOT NULL;

-- Add comment for clarity
COMMENT ON COLUMN ev_log.cost_eur IS 'Cost in EUR - nullable for auto-imported logs (e.g., TESLA_IMPORT) where cost is unknown';
