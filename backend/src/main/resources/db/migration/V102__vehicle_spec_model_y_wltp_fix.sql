-- V102: Tesla Model Y - WLTP-Werte korrigieren und Juniper-Varianten ergaenzen
--
-- Befund:
--   60 kWh (Standard Range / RWD Juniper): hatte EPA-Werte (418 km, 17.4 kWh/100km),
--     obwohl variant_name 'Model Y RWD Juniper (2025-2026)' lautet.
--     Korrekte WLTP Juniper-Werte: 500 km, 12.0 kWh/100km (120 Wh/km).
--   75 kWh (LR AWD 2022-2025): Verbrauch 16.3 unveraendert (TEH-Wert nicht verfuegbar),
--     nur available_from/to gesetzt.
--   79 kWh (Performance 2022-2025): variant_name war irreführend 'Performance / LR AWD Juniper'.
--     Verbrauch 17.5 -> 17.1 kWh/100km korrigiert. available_to bis Juli 2025 gesetzt.
--   Neu: LR AWD Juniper (2025+) als eigene Spec bei 78.4 kWh nominal.
--   Neu: Performance AWD Juniper (2025+) bei 79.0 kWh nominal.
--
-- Quelle: ev-database.org (car/1619, car/1183, car/3103, car/3104, car/3269), April 2026
--   Rated consumption = "official figures as published by manufacturer, includes charging losses"

-- 1. Standard Range / RWD Juniper: EPA-Werte durch korrekte WLTP-Werte ersetzen
--    418 km / 17.4 kWh (EPA) -> 500 km / 12.0 kWh (WLTP, 120 Wh/km rated incl. losses)
UPDATE vehicle_specification
SET official_range_km                  = 500,
    official_consumption_kwh_per_100km = 12.0,
    rating_source                      = 'WLTP',
    available_from                     = '2025-01-01'
WHERE car_brand = 'TESLA'
  AND car_model = 'MODEL_Y'
  AND battery_capacity_kwh = 60.00;

-- 2. LR AWD (2022-2025): Zeitraum setzen, Verbrauch 16.3 unveraendert
--    ev-database car/1619 liefert nur TEL (169 Wh/km); TEH-Wert fehlt.
--    16.3 kam aus V8 "official manufacturer figures" - kein sicherer Ersatz verfuegbar.
UPDATE vehicle_specification
SET available_from = '2022-01-01',
    available_to   = '2025-01-31'
WHERE car_brand = 'TESLA'
  AND car_model = 'MODEL_Y'
  AND battery_capacity_kwh = 75.00;

-- 3. Performance AWD (2022-2025): variant_name bereinigen + Verbrauch + Zeitraum
--    17.5 -> 17.1 kWh/100km (171 Wh/km rated incl. losses, ev-database car/1183)
--    available_to Juli 2025: Performance Juniper kam August 2025
UPDATE vehicle_specification
SET variant_name                       = 'Model Y Performance AWD (2022-2025)',
    official_consumption_kwh_per_100km = 17.1,
    available_from                     = '2022-01-01',
    available_to                       = '2025-07-31'
WHERE car_brand = 'TESLA'
  AND car_model = 'MODEL_Y'
  AND battery_capacity_kwh = 79.00;

-- 4. LR AWD Juniper (2025+): neue Spec einfuegen (78.4 kWh nominal = 75 kWh netto)
--    568 km / 15.3 kWh/100km (153 Wh/km TEH rated incl. losses, ev-database car/3104)
INSERT INTO vehicle_specification (
    id,
    car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from,
    created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'TESLA', 'MODEL_Y',
    78.40, 75.0,
    568, 15.3,
    'COMBINED', 'WLTP',
    'Model Y LR AWD Juniper (2025+)', 'Long Range AWD',
    '2025-01-01',
    NOW(), NOW()
);

-- 5. Performance AWD Juniper (2025+): neue Spec einfuegen (79.0 kWh nominal = netto)
--    580 km / 16.2 kWh/100km (162 Wh/km rated incl. losses, ev-database car/3269)
--    verfuegbar seit August 2025
INSERT INTO vehicle_specification (
    id,
    car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from,
    created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'TESLA', 'MODEL_Y',
    79.00, 79.0,
    580, 16.2,
    'COMBINED', 'WLTP',
    'Model Y Performance AWD Juniper (2025+)', 'Performance',
    '2025-08-01',
    NOW(), NOW()
);
