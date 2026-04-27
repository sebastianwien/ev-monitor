-- Migrate legacy logs where vehicle-side energy was stored in kwh_charged with measurement_type=AT_VEHICLE.
-- After this migration, kwh_at_vehicle is always the canonical field for vehicle-side measurements.
-- Affected sources: SMARTCAR_LIVE, TESLA_LIVE, TESSIE, and any API_UPLOAD with explicit AT_VEHICLE.

UPDATE ev_log
SET kwh_at_vehicle = kwh_charged,
    kwh_charged    = NULL
WHERE measurement_type = 'AT_VEHICLE'
  AND kwh_charged IS NOT NULL
  AND kwh_at_vehicle IS NULL;
