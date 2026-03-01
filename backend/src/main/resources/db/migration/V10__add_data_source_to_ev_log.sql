-- Add data_source column to track origin of charging logs
-- For transparency and proper attribution of imported data

ALTER TABLE ev_log
ADD COLUMN data_source VARCHAR(50) DEFAULT 'USER_LOGGED';

-- Possible values:
-- 'USER_LOGGED' - Manually entered by user (default)
-- 'SPRITMONITOR_IMPORT' - Imported from Sprit-Monitor API
-- Future: 'ADAC_REFERENCE', 'OPEN_DATA', etc.

COMMENT ON COLUMN ev_log.data_source IS
  'Source of the charging log entry for transparency and attribution. Defaults to USER_LOGGED for manual entries.';

-- Set existing logs to USER_LOGGED (explicit default for existing data)
UPDATE ev_log SET data_source = 'USER_LOGGED' WHERE data_source IS NULL;
