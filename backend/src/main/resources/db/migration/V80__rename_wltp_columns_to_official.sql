-- Rename misnamed columns: these hold official rating values for both WLTP and EPA rows.
-- The old names (wltp_range_km, wltp_consumption_kwh_per_100km) were misleading for EPA rows.
-- official_range_km / official_consumption_kwh_per_100km are source-agnostic.

ALTER TABLE vehicle_specification
    RENAME COLUMN wltp_range_km TO official_range_km;

ALTER TABLE vehicle_specification
    RENAME COLUMN wltp_consumption_kwh_per_100km TO official_consumption_kwh_per_100km;
