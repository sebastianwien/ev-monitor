-- Opel Ampera-e (BEV, Chevrolet Bolt Basis, 2017-2019)
INSERT INTO vehicle_spec (id, brand, model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES (gen_random_uuid(), 'OPEL', 'AMPERA_E', 60.0, 413, 17.0, 'COMBINED', NOW(), NOW());
