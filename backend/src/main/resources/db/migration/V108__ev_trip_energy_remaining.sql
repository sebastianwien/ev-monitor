ALTER TABLE ev_trip ADD COLUMN IF NOT EXISTS energy_remaining_start_kwh NUMERIC(7,3);
ALTER TABLE ev_trip ADD COLUMN IF NOT EXISTS energy_remaining_end_kwh NUMERIC(7,3);
