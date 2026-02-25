-- Add odometer and max charging power tracking to ev_log
-- Phase 1: User Tracking Enhancement

ALTER TABLE ev_log ADD COLUMN odometer_km INTEGER;
ALTER TABLE ev_log ADD COLUMN max_charging_power_kw NUMERIC(10,2);

CREATE INDEX idx_ev_log_odometer ON ev_log(car_id, odometer_km);

COMMENT ON COLUMN ev_log.odometer_km IS 'Tachostand in km (optional)';
COMMENT ON COLUMN ev_log.max_charging_power_kw IS 'Maximale Ladeleistung in kW (optional)';
