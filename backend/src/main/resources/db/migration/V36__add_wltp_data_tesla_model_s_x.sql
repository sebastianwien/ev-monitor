INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    -- Tesla Model S (2021 Refresh, Long Range AWD, 95 kWh usable)
    (gen_random_uuid(), 'TESLA', 'MODEL_S', 95.0, 652, 14.6, 'COMBINED', NOW(), NOW()),
    -- Tesla Model X (2021 Refresh, Long Range AWD, 95 kWh usable)
    (gen_random_uuid(), 'TESLA', 'MODEL_X', 95.0, 576, 16.5, 'COMBINED', NOW(), NOW())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, wltp_type) DO NOTHING;
