-- Add soc_start_percent to track battery level at the START of a charging session.
-- This complements the existing soc_after_charge_percent (battery level at end).
-- Useful for TeslaLogger/TeslaMate imports and manual entries where both values are known.
ALTER TABLE ev_log ADD COLUMN soc_start_percent INTEGER;
