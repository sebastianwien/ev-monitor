-- At which point in the charging chain energy is measured.
-- AT_CHARGER (Level 1): wallboxes, charge stations, OCPP — gross energy (default for all existing data)
-- AT_VEHICLE (Level 2): vehicle API (Smartcar, Tesla Fleet live) — net energy entering the battery (~7% less)
-- DRIVING_ONLY (Level 3): drive consumption only, excludes standby/preconditioning (~20-30% less)
ALTER TABLE ev_log
    ADD COLUMN measurement_type VARCHAR(20) NOT NULL DEFAULT 'AT_CHARGER';

-- Correct historical TESLA_LIVE logs — they also report AT_VEHICLE
UPDATE ev_log SET measurement_type = 'AT_VEHICLE' WHERE data_source = 'TESLA_LIVE';
