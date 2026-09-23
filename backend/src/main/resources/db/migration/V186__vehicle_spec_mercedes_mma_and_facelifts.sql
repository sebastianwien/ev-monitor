-- V186: Mercedes - MMA-Generation (CLA, GLA, GLB, GLC), G 580, EQT sowie EQS/EQS SUV Facelift
--
-- Befund: vehicle_specification kannte fuer Mercedes nur die EQ-Generation (EQA bis EQV).
-- Es fehlten alle Modelle ab 2025 (CLA, GLA, GLB, GLC mit EQ-Technologie), der G 580,
-- der EQT und die Facelift-Akkus von EQS und EQS SUV (118 kWh netto statt 107.8/108.4).
--
-- Konvention wie bei V154/V184: battery_capacity_kwh = brutto (Lookup-Key),
-- net_battery_capacity_kwh = nutzbar, Verbrauch = WLTP "rated consumption"
-- (Herstellerangabe inkl. Ladeverluste), Reichweite = WLTP TEL.
--
-- Quelle: ev-database.org, manuell gelesen am 23.09.2026:
--   CLA 200 /de/pkw/3370, CLA 250 car/3473, CLA 250+ car/3139, CLA 350 4MATIC car/3140,
--   CLA SB 250 car/3688, CLA SB 250+ car/3396, CLA SB 350 4MATIC car/3397,
--   GLA 250+ car/3686, GLA 350 4MATIC car/3687, GLB 250+ car/3406, GLB 350 4MATIC car/3407,
--   GLC 300 4MATIC car/3654, GLC 400 4MATIC car/3296, G 580 car/2192,
--   EQT 200 Standard car/1908, EQT 200 Long car/2239,
--   EQS 450+ car/1483 (2021-23), car/2008 (2023-24), car/2193 (2024-26), car/3578 (MY26),
--   EQS 580 4MATIC car/1484, car/2011, car/2196, car/3580, EQS 450 4MATIC car/2194,
--   EQS SUV 450+ car/1673, car/2087; 450 4MATIC car/1674, car/2088; 580 4MATIC car/1675, car/2090.
-- available_from nur dort gesetzt, wo ev-database einen Monat nennt. Bei Modellgruppen,
-- in denen nicht alle Varianten datierbar sind, bleibt die ganze Gruppe undatiert,
-- weil das UI undatierte Optionen ausblendet, sobald eine Gruppe datierte hat.

-- ============================================================
-- 1. Neue Modelle
-- ============================================================
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', v.modell, v.brutto, v.netto, v.rw, v.verbrauch,
       'COMBINED', 'WLTP', v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    -- CLA Limousine (alle Varianten datierbar)
    ('CLA',  60.00, 58.0, 542, 12.3, 'CLA 200 (LFP)',   'CLA 200',        DATE '2025-10-01', NULL::date),
    ('CLA',  90.00, 71.0, 673, 12.4, 'CLA 250',         'CLA 250',        DATE '2026-02-01', NULL),
    ('CLA',  90.00, 85.0, 792, 12.3, 'CLA 250+',        'CLA 250+',       DATE '2025-05-01', NULL),
    ('CLA',  90.00, 85.0, 770, 12.7, 'CLA 350 4MATIC',  'CLA 350 4MATIC', DATE '2025-05-01', NULL),
    -- CLA Shooting Brake (250+/350 nur mit Jahr 2025 belegt, daher Gruppe undatiert)
    ('CLA_SHOOTING_BRAKE', 90.00, 71.0, 652, 12.8, 'CLA 250 Shooting Brake',        'CLA 250',        NULL, NULL),
    ('CLA_SHOOTING_BRAKE', 90.00, 85.0, 768, 12.7, 'CLA 250+ Shooting Brake',       'CLA 250+',       NULL, NULL),
    ('CLA_SHOOTING_BRAKE', 90.00, 85.0, 743, 13.2, 'CLA 350 4MATIC Shooting Brake', 'CLA 350 4MATIC', NULL, NULL),
    -- GLA (beide ab Juli 2026)
    ('GLA',  90.00, 85.0, 657, 15.1, 'GLA 250+',        'GLA 250+',       DATE '2026-07-01', NULL),
    ('GLA',  90.00, 85.0, 643, 15.1, 'GLA 350 4MATIC',  'GLA 350 4MATIC', DATE '2026-07-01', NULL),
    -- GLB (nur Jahr 2025 belegt)
    ('GLB',  90.00, 85.0, 631, 15.8, 'GLB 250+',        'GLB 250+',       NULL, NULL),
    ('GLB',  90.00, 85.0, 614, 15.9, 'GLB 350 4MATIC',  'GLB 350 4MATIC', NULL, NULL),
    -- GLC (400: Bestellstart Oktober 2025, 300: ab Juni 2026)
    ('GLC', 100.00, 94.0, 715, 14.9, 'GLC 400 4MATIC',  'GLC 400 4MATIC', DATE '2025-10-01', NULL),
    ('GLC',  90.00, 85.0, 613, 16.0, 'GLC 300 4MATIC',  'GLC 300 4MATIC', DATE '2026-06-01', NULL),
    -- G 580 mit EQ Technologie
    ('G_580', 124.00, 116.0, 473, 27.7, 'G 580 (2024-)', 'G 580', NULL, NULL),
    -- EQT (Standard ab 06/2023, Lang ab 06/2024, beide bis 07/2026)
    ('EQT',  48.00, 45.0, 282, 18.9, 'EQT 200',         'Standard',       DATE '2023-06-01', DATE '2026-07-31'),
    ('EQT',  48.00, 45.0, 266, 20.6, 'EQT 200 Lang',    'Lang',           DATE '2024-06-01', DATE '2026-07-31')
) AS v(modell, brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = v.modell AND x.variant_name = v.variante);

-- ============================================================
-- 2. EQS: Generationen aufloesen
-- ============================================================
-- Das Formular gruppiert nach trim_level und blendet Zeilen ohne trim_level aus,
-- sobald ein Modell eine Zeile mit trim_level hat. Die bestehende EQS-Zeile
-- (107.8 kWh, ohne Variante, 770 km) wird deshalb zur ersten 450+-Generation
-- (car/1483) und bekommt wie alle Tesla/XPeng-Zeilen brutto als Lookup-Key.
-- Weder lokal noch auf Prod haengt ein Auto an EQS- oder EQS-SUV-Zeilen (Stand 23.09.2026).
UPDATE vehicle_specification
SET battery_capacity_kwh               = 120.00,
    net_battery_capacity_kwh           = 107.8,
    official_range_km                  = 784,
    official_consumption_kwh_per_100km = 15.6,
    variant_name   = 'EQS 450+ (2021-2023)',
    trim_level     = '450+',
    available_from = '2021-10-01',
    available_to   = '2023-05-31',
    updated_at     = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQS'
  AND battery_capacity_kwh = 107.80 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQS', v.brutto, v.netto, v.rw, v.verbrauch,
       'COMBINED', 'WLTP', v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    -- Generation 1 (10/2021 - 05/2023), 120 kWh brutto / 107.8 netto
    (120.00, 107.8, 672, 17.7, 'EQS 580 4MATIC (2021-2023)',          '580 4MATIC', DATE '2021-10-01', DATE '2023-05-31'),
    -- Zwischenstand (06/2023 - 03/2024), 108.4 netto
    (120.00, 108.4, 780, 15.8, 'EQS 450+ (2023-2024)',                '450+',       DATE '2023-06-01', DATE '2024-03-31'),
    (120.00, 108.4, 719, 17.2, 'EQS 580 4MATIC (2023-2024)',          '580 4MATIC', DATE '2023-06-01', DATE '2024-03-31'),
    -- Facelift (04/2024 - 03/2026), 125 kWh brutto / 118 netto
    (125.00, 118.0, 822, 16.3, 'EQS 450+ Facelift (2024-2026)',       '450+',       DATE '2024-04-01', DATE '2026-03-31'),
    (125.00, 118.0, 798, 17.0, 'EQS 450 4MATIC Facelift (2024-2026)', '450 4MATIC', DATE '2024-04-01', DATE '2026-03-31'),
    (125.00, 118.0, 798, 17.0, 'EQS 580 4MATIC Facelift (2024-2026)', '580 4MATIC', DATE '2024-04-01', DATE '2026-03-31'),
    -- MY26 (ab 04/2026), 129 kWh brutto / 122 netto
    (129.00, 122.0, 920, 15.5, 'EQS 450+ (MY26)',                     '450+',       DATE '2026-04-01', NULL),
    (129.00, 122.0, 869, 16.3, 'EQS 580 4MATIC (MY26)',               '580 4MATIC', DATE '2026-04-01', NULL)
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQS' AND x.variant_name = v.variante);

-- ============================================================
-- 3. EQS SUV: Generationen aufloesen
-- ============================================================
-- Bestehende Zeile (108.4 kWh, ohne Variante, 660 km) wird zum 450+ der ersten
-- Generation (car/1673). Die Zeile '100/94 kWh' entspricht keiner offiziellen
-- EQS-SUV-Variante und haengt an keinem Auto; sie wird entfernt.
UPDATE vehicle_specification
SET battery_capacity_kwh               = 120.00,
    net_battery_capacity_kwh           = 108.4,
    official_range_km                  = 672,
    official_consumption_kwh_per_100km = 18.2,
    variant_name   = 'EQS SUV 450+ (2022-2023)',
    trim_level     = '450+',
    available_from = '2022-12-01',
    available_to   = '2023-09-30',
    updated_at     = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQS_SUV'
  AND battery_capacity_kwh = 108.40 AND variant_name = '';

DELETE FROM vehicle_specification
WHERE car_brand = 'MERCEDES' AND car_model = 'EQS_SUV' AND variant_name = '100/94 kWh'
  AND NOT EXISTS (SELECT 1 FROM car c WHERE c.vehicle_specification_id = vehicle_specification.id);

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQS_SUV', v.brutto, v.netto, v.rw, v.verbrauch,
       'COMBINED', 'WLTP', v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    -- Generation 1, 120 kWh brutto / 108.4 netto (Zeitraeume laut ev-database, leichte Ueberlappung)
    (120.00, 108.4, 616, 19.9, 'EQS SUV 450 4MATIC (2023)',            '450 4MATIC', DATE '2023-03-01', DATE '2023-12-31'),
    (120.00, 108.4, 615, 20.0, 'EQS SUV 580 4MATIC (2022-2023)',       '580 4MATIC', DATE '2022-12-01', DATE '2023-12-31'),
    -- Facelift, 125 kWh brutto / 118 netto
    (125.00, 118.0, 720, 18.8, 'EQS SUV 450+ Facelift (2023-)',        '450+',       DATE '2023-10-01', NULL),
    (125.00, 118.0, 673, 20.0, 'EQS SUV 450 4MATIC Facelift (2024-)',  '450 4MATIC', DATE '2024-01-01', NULL),
    (125.00, 118.0, 673, 20.0, 'EQS SUV 580 4MATIC Facelift (2023-)',  '580 4MATIC', DATE '2023-10-01', NULL)
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQS_SUV' AND x.variant_name = v.variante);
