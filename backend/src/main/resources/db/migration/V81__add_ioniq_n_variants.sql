-- Hyundai Ioniq 5 N und Ioniq 6 N WLTP-Daten.
-- Quelle: EVKX.net / offizielle Hyundai-Angaben.
-- Beide Modelle nutzen denselben 84 kWh Brutto- / 80 kWh Netto-Akku.
-- Ioniq 5 N: AWD, 601 PS - signifikant höherer Verbrauch als Standard-Ioniq 5.
-- Ioniq 6 N: RWD als Referenz-Variante (AWD: 519 km / 15.4 kWh/100km).

INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5_N', 80.0, 450, 17.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6_N', 80.0, 583, 13.7, 'COMBINED', NOW(), NOW());
