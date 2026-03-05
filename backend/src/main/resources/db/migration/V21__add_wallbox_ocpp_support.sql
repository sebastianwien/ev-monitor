-- Support for WALLBOX_OCPP data source
-- Odometer suggestion from wallbox service (km estimate based on session kWh)
ALTER TABLE ev_log ADD COLUMN odometer_suggestion_min_km INTEGER;
ALTER TABLE ev_log ADD COLUMN odometer_suggestion_max_km INTEGER;
