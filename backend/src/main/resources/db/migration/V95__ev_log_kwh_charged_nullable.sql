-- kwh_charged is now optional when kwh_at_vehicle is provided (AT_VEHICLE measurements).
ALTER TABLE ev_log ALTER COLUMN kwh_charged DROP NOT NULL;
