-- Add rating_source discriminator to support EPA ratings alongside WLTP.
-- All existing rows are WLTP — backfilled via DEFAULT.
-- Column names (wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type) are kept as-is;
-- for EPA rows they hold the EPA values in km / kWh/100km / cycle type respectively.

ALTER TABLE vehicle_specification
    ADD COLUMN rating_source VARCHAR(10) NOT NULL DEFAULT 'WLTP';

ALTER TABLE vehicle_specification
    DROP CONSTRAINT uq_vehicle_spec;

ALTER TABLE vehicle_specification
    ADD CONSTRAINT uq_vehicle_spec
    UNIQUE (car_brand, car_model, battery_capacity_kwh, wltp_type, rating_source);
