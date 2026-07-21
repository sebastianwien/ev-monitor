-- Kia EV3 (EU-Markt, seit 2024): stand bisher nur im CarModel-Enum, hatte aber keine
-- vehicle_specification-Zeile - die WLTP-basierte Verbrauchs-Plausibilisierung griff dadurch nicht.
-- Zwei Batteriegroessen, beide FWD/Single-Motor. Lookup-Keys battery_capacity_kwh = die
-- CarModel-Enum cap()-Werte (58.3 / 81.4). Anleitung: docs/development/vehicle-specifications.md
-- Quellen: EV Database (Standard Range: 58.3/55.0 kWh, 436 km, 14.9 kWh/100km;
--          Long Range: 81.4/78.0 kWh, 605 km, 14.9 kWh/100km).
-- Je Kapazitaet nur eine Variante -> keine pickBestMatch-Kollision, available_from nur dokumentarisch.
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'KIA', 'EV_3', 58.30, 55.00, 436, 14.9, 'COMBINED', 'WLTP',
 'EV3 58.3 kWh Standard Range (2024-)', 'Standard Range', '2024-09-01', NULL, NOW(), NOW()),
(gen_random_uuid(), 'KIA', 'EV_3', 81.40, 78.00, 605, 14.9, 'COMBINED', 'WLTP',
 'EV3 81.4 kWh Long Range (2024-)', 'Long Range', '2024-09-01', NULL, NOW(), NOW());
