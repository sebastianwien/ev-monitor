INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at)
VALUES (gen_random_uuid(), 'TESLA', 'MODEL_3', 73.50, 600.00, 14.70, 'COMBINED', 'WLTP', now(), now());
