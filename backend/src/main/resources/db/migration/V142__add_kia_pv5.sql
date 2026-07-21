-- Kia PV5 Passenger (EU-Markt, Auslieferung seit 10/2025): fehlte komplett.
-- Elektrischer PBV-Van, zwei NMC-Akkus, beide FWD/Single-Motor. Lookup-Keys battery_capacity_kwh
-- = CarModel-Enum cap()-Werte (51.5 / 71.2). trim_level von Anfang an gesetzt (saubere FE-Labels).
-- Quellen: EV Database (SR: 51.5/48.0 kWh, 295 km, rated 192 Wh/km; LR: 71.2/67.0 kWh, 412 km,
--          rated 193 Wh/km). Verbrauch = WLTP-Mittelwert; Kia nennt kombiniert 19.1-19.3 kWh/100km
--          (SR 19.2, LR 19.3 - je Variante praktisch Einzelwerte).
-- Je Kapazitaet nur eine Variante -> keine pickBestMatch-Kollision.
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'KIA', 'PV_5', 51.50, 48.00, 295, 19.2, 'COMBINED', 'WLTP',
 'PV5 Passenger 51.5 kWh Standard Range (2025-)', 'Standard Range', '2025-10-01', NULL, NOW(), NOW()),
(gen_random_uuid(), 'KIA', 'PV_5', 71.20, 67.00, 412, 19.3, 'COMBINED', 'WLTP',
 'PV5 Passenger 71.2 kWh Long Range (2025-)', 'Long Range', '2025-10-01', NULL, NOW(), NOW());
