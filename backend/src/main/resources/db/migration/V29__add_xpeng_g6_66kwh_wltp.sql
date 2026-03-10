-- Add missing WLTP spec for XPENG G6 Standard Range (66 kWh, Chinese/EU base variant).
-- Was never inserted when the other G6 variants were added to vehicle_specification.

INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES (gen_random_uuid(), 'XPENG', 'XPENG_G6', 66.0, 435, 17.1, 'COMBINED', NOW(), NOW());
