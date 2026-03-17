-- Tesla Model S 75D (75 kWh, 2016-2019)
-- WLTP Combined: ~455 km, ~16.5 kWh/100km (EU Typgenehmigung)
INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'TESLA', 'MODEL_S', 75.0, 455, 16.5, 'COMBINED', NOW(), NOW()),
    -- Tesla Model S Plaid (2021+, 95 kWh usable)
    -- WLTP Combined: ~396 km, ~24.0 kWh/100km
    (gen_random_uuid(), 'TESLA', 'MODEL_S_PLAID', 95.0, 396, 24.0, 'COMBINED', NOW(), NOW()),
    -- Tesla Model S Performance Raven (2019-2021, 95 kWh usable)
    -- WLTP Combined: 639 km, ~14.9 kWh/100km (Quelle: ecomento.de, 2020-10-19)
    (gen_random_uuid(), 'TESLA', 'MODEL_S_PERFORMANCE', 95.0, 639, 14.9, 'COMBINED', NOW(), NOW())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, wltp_type) DO NOTHING;
