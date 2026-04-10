INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    -- Hyundai Inster (Netto: 39.0 / 46.0 kWh; Brutto: 41.0 / 49.0 kWh)
    (gen_random_uuid(), 'HYUNDAI', 'INSTER', 39.0, 300, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'INSTER', 46.0, 355, 15.3, 'COMBINED', NOW(), NOW()),
    -- Hyundai Ioniq 9 (107.0 kWh netto / 110.3 kWh brutto; RWD Long Range als Referenz-Variante)
    -- AWD Long Range: 606 km / AWD Performance: 570 km — selbe Batterie, andere WLTP-Werte
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_9', 107.0, 620, 17.7, 'COMBINED', NOW(), NOW());
