-- Allow nullable kwh_charged and charge_duration_minutes for wallbox imports.
-- We prefer storing incomplete data over losing it entirely — can be corrected later.
ALTER TABLE ev_log ALTER COLUMN kwh_charged DROP NOT NULL;
ALTER TABLE ev_log ALTER COLUMN charge_duration_minutes DROP NOT NULL;
