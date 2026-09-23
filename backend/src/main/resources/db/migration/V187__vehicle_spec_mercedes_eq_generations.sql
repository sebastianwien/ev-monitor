-- V187: Mercedes EQ-Generation (EQA, EQB, EQC, EQE, EQE SUV, EQV) nach Trim und Zeitraum aufloesen,
--       CLA Shooting Brake / GLB / G 580 aus V186 datieren
--
-- Befund: Die Alt-Zeilen hatten weder trim_level noch Zeitraum, Varianten hiessen "74/70 kWh".
-- Nutzer erkennen ihr Auto ueber Baujahr und Ausfuehrung, nicht ueber kWh.
--
-- Konvention wie V154/V184/V186: battery_capacity_kwh = brutto (Lookup-Key), net = nutzbar,
-- Verbrauch = WLTP rated consumption (inkl. Ladeverluste), Reichweite = WLTP TEL.
--
-- Verknuepfte Autos (Prod 23.09.2026, 8 Stueck) bleiben an ihrer Zeile, weil die Alt-Zeilen
-- per UPDATE in die passende Variante umgewandelt werden (id bleibt). Es wird kein Auto
-- umgehaengt. Die Zeile EQE_SUV '94/94 kWh' bleibt bestehen (siehe Abschnitt 5).
--
-- Quelle: ev-database.org, manuell gelesen am 23.09.2026:
--   EQA 250 car/1147, /1697; 250+ /1665, /1985; 300 4MATIC /1495, /1986; 350 4MATIC /1496, /1987
--   EQB 250 /1664; 250+ /1988; 300 4MATIC /1559, /1989; 350 4MATIC /1493, /1990
--   EQC 400 4MATIC /1135, /1337
--   EQE 300 /1713; 350 /1898; 350+ /2006, /2205, /3240; 350 4MATIC /1715, /1899, /2206, /3241;
--       500 4MATIC /1716, /1900, /2207
--   EQE SUV 350+ /2046, /3284; 350 4MATIC /1761, /2047, /3285; 500 4MATIC /1762, /2048, /3286
--   EQV 250 Lang /1542, /2129; 300 Lang /1240, /2131; 300 Extralang /2274
--   CLA SB /3396, /3397; GLB /3406, /3407; G 580 /2192
-- Nicht aufgenommen (ev-database hat den Abruf gedrosselt): EQE 350+ MY22-23 (/1538),
--   EQE 300 MY23/MY24/MY25 (/1897, /2204, /3239), EQE 500 4MATIC MY25 (/3242),
--   EQE SUV 350+ MY23 (/1760), AMG-Varianten. Koennen in einer Folgemigration ergaenzt werden.

-- ============================================================
-- 1. EQA
-- ============================================================
-- Alt-Zeile 66.5 (492 km) -> EQA 250 (2022-2023), car/1697. Daran haengen zwei Autos
-- (Baujahr 2022 und 2026); das 2026er ist vermutlich ein 300/350 4MATIC, das kann nur
-- der Nutzer korrigieren.
UPDATE vehicle_specification
SET battery_capacity_kwh = 69.70, net_battery_capacity_kwh = 66.5,
    official_range_km = 496, official_consumption_kwh_per_100km = 15.4,
    variant_name = 'EQA 250 (2022-2023)', trim_level = '250',
    available_from = '2022-06-01', available_to = '2023-10-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQA'
  AND battery_capacity_kwh = 66.50 AND variant_name = '';

-- Alt-Zeile '74/70 kWh' (553 km) -> EQA 250+ (2023-2026), car/1985
UPDATE vehicle_specification
SET battery_capacity_kwh = 73.90, net_battery_capacity_kwh = 70.5,
    official_range_km = 560, official_consumption_kwh_per_100km = 14.4,
    variant_name = 'EQA 250+ (2023-2026)', trim_level = '250+',
    available_from = '2023-10-01', available_to = '2026-08-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQA' AND variant_name = '74/70 kWh';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQA', v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    (69.70, 66.5, 429, 17.7, 'EQA 250 (2021-2022)',        '250',        DATE '2021-04-01', DATE '2022-05-31'),
    (73.90, 70.5, 540, 14.9, 'EQA 250+ (2022)',            '250+',       DATE '2022-03-01', DATE '2022-06-30'),
    (69.70, 66.5, 438, 17.5, 'EQA 300 4MATIC (2021-2023)', '300 4MATIC', DATE '2021-05-01', DATE '2023-11-30'),
    (69.70, 66.5, 459, 16.7, 'EQA 300 4MATIC (2023-2026)', '300 4MATIC', DATE '2023-10-01', DATE '2026-08-31'),
    (69.70, 66.5, 438, 17.5, 'EQA 350 4MATIC (2021-2023)', '350 4MATIC', DATE '2021-05-01', DATE '2023-11-30'),
    (69.70, 66.5, 459, 16.7, 'EQA 350 4MATIC (2023-2026)', '350 4MATIC', DATE '2023-10-01', DATE '2026-08-31')
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQA' AND x.variant_name = v.variante);

-- ============================================================
-- 2. EQB
-- ============================================================
-- Alt-Zeile 66.5 (419 km): daran haengt ein 2025er mit Trim "300" -> EQB 300 4MATIC (2023-2026), car/1989
UPDATE vehicle_specification
SET battery_capacity_kwh = 69.70, net_battery_capacity_kwh = 66.5,
    official_range_km = 448, official_consumption_kwh_per_100km = 17.1,
    variant_name = 'EQB 300 4MATIC (2023-2026)', trim_level = '300 4MATIC',
    available_from = '2023-10-01', available_to = '2026-01-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQB'
  AND battery_capacity_kwh = 66.50 AND variant_name = '';

-- Alt-Zeile 70.0 (535 km): daran haengt ein 2025er mit Trim "250+" -> EQB 250+ (2023-2026), car/1988
UPDATE vehicle_specification
SET battery_capacity_kwh = 73.90, net_battery_capacity_kwh = 70.5,
    official_range_km = 536, official_consumption_kwh_per_100km = 15.2,
    variant_name = 'EQB 250+ (2023-2026)', trim_level = '250+',
    available_from = '2023-10-01', available_to = '2026-01-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQB'
  AND battery_capacity_kwh = 70.00 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQB', v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    (69.70, 66.5, 474, 16.3, 'EQB 250 (2022-2023)',        '250',        DATE '2022-03-01', DATE '2023-10-31'),
    (69.70, 66.5, 423, 18.1, 'EQB 300 4MATIC (2022-2023)', '300 4MATIC', DATE '2022-02-01', DATE '2023-11-30'),
    (69.70, 66.5, 423, 18.1, 'EQB 350 4MATIC (2022-2023)', '350 4MATIC', DATE '2022-02-01', DATE '2023-11-30'),
    (69.70, 66.5, 459, 17.1, 'EQB 350 4MATIC (2023-2026)', '350 4MATIC', DATE '2023-10-01', DATE '2026-01-31')
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQB' AND x.variant_name = v.variante);

-- ============================================================
-- 3. EQC
-- ============================================================
UPDATE vehicle_specification
SET battery_capacity_kwh = 85.00, net_battery_capacity_kwh = 80.0,
    official_range_km = 411, official_consumption_kwh_per_100km = 22.3,
    variant_name = 'EQC 400 4MATIC (2020-2023)', trim_level = '400 4MATIC',
    available_from = '2020-11-01', available_to = '2023-07-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQC'
  AND battery_capacity_kwh = 80.00 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQC', 85.00, 80.0, 417, 22.3, 'COMBINED', 'WLTP',
       'EQC 400 4MATIC (2019-2020)', '400 4MATIC', DATE '2019-07-01', DATE '2020-10-31', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQC' AND x.variant_name = 'EQC 400 4MATIC (2019-2020)');

-- ============================================================
-- 4. EQE
-- ============================================================
-- Alt-Zeile 89.0 (659 km): daran haengt ein 2022er mit Trim "300 AMG Line" -> EQE 300 (MY22-23), car/1713
UPDATE vehicle_specification
SET battery_capacity_kwh = 98.00, net_battery_capacity_kwh = 89.0,
    official_range_km = 639, official_consumption_kwh_per_100km = 16.0,
    variant_name = 'EQE 300 (MY22-23)', trim_level = '300',
    available_from = '2022-07-01', available_to = '2023-09-30', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQE'
  AND battery_capacity_kwh = 89.00 AND variant_name = '';

-- Alt-Zeile 90.6 (550 km, kein Auto) -> EQE 350 4MATIC (MY22-23), car/1715
UPDATE vehicle_specification
SET battery_capacity_kwh = 100.00, net_battery_capacity_kwh = 90.6,
    official_range_km = 597, official_consumption_kwh_per_100km = 17.5,
    variant_name = 'EQE 350 4MATIC (MY22-23)', trim_level = '350 4MATIC',
    available_from = '2022-07-01', available_to = '2023-09-30', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQE'
  AND battery_capacity_kwh = 90.60 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQE', v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    ( 98.00, 89.0, 621, 16.5, 'EQE 350 (MY23)',         '350',        DATE '2022-09-01', DATE '2024-07-31'),
    (100.00, 90.6, 671, 15.8, 'EQE 350+ (MY23)',        '350+',       DATE '2023-06-01', DATE '2024-03-31'),
    (105.00, 96.0, 693, 16.0, 'EQE 350+ (MY24)',        '350+',       DATE '2024-04-01', DATE '2025-06-30'),
    (105.00, 96.0, 691, 16.1, 'EQE 350+ (MY25)',        '350+',       DATE '2025-04-01', NULL),
    ( 98.00, 89.0, 614, 17.0, 'EQE 350 4MATIC (MY23)',  '350 4MATIC', DATE '2023-06-01', DATE '2024-03-31'),
    (100.00, 90.6, 627, 16.7, 'EQE 350 4MATIC (MY24)',  '350 4MATIC', DATE '2024-04-01', DATE '2025-03-31'),
    (100.00, 90.6, 625, 16.8, 'EQE 350 4MATIC (MY25)',  '350 4MATIC', DATE '2025-04-01', NULL),
    (100.00, 90.6, 596, 17.5, 'EQE 500 4MATIC (MY22-23)', '500 4MATIC', DATE '2022-07-01', DATE '2023-09-30'),
    (100.00, 90.6, 620, 16.8, 'EQE 500 4MATIC (MY23)',  '500 4MATIC', DATE '2023-06-01', DATE '2024-03-31'),
    (100.00, 90.6, 623, 16.9, 'EQE 500 4MATIC (MY24)',  '500 4MATIC', DATE '2024-04-01', DATE '2025-06-30')
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQE' AND x.variant_name = v.variante);

-- ============================================================
-- 5. EQE SUV
-- ============================================================
-- Alt-Zeile 96.0 (490 km, kein Auto) -> EQE SUV 350+ (MY23-24), car/2046
UPDATE vehicle_specification
SET battery_capacity_kwh = 105.00, net_battery_capacity_kwh = 96.0,
    official_range_km = 628, official_consumption_kwh_per_100km = 17.7,
    variant_name = 'EQE SUV 350+ (MY23-24)', trim_level = '350+',
    available_from = '2023-10-01', available_to = '2025-04-30', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQE_SUV'
  AND battery_capacity_kwh = 96.00 AND variant_name = '';

-- Alt-Zeile 90.6 (590 km, kein Auto) -> EQE SUV 350 4MATIC (MY23-24), car/2047
UPDATE vehicle_specification
SET battery_capacity_kwh = 100.00, net_battery_capacity_kwh = 90.6,
    official_range_km = 566, official_consumption_kwh_per_100km = 18.3,
    variant_name = 'EQE SUV 350 4MATIC (MY23-24)', trim_level = '350 4MATIC',
    available_from = '2023-10-01', available_to = '2025-04-30', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQE_SUV'
  AND battery_capacity_kwh = 90.60 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQE_SUV', v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    (105.00, 96.0, 611, 18.3, 'EQE SUV 350+ (MY25)',          '350+',       DATE '2025-04-01', NULL),
    ( 98.00, 89.0, 558, 18.5, 'EQE SUV 350 4MATIC (MY23)',    '350 4MATIC', DATE '2022-12-01', DATE '2024-01-31'),
    (100.00, 90.6, 555, 19.0, 'EQE SUV 350 4MATIC (MY25)',    '350 4MATIC', DATE '2025-04-01', NULL),
    (100.00, 90.6, 547, 19.0, 'EQE SUV 500 4MATIC (MY23)',    '500 4MATIC', DATE '2022-12-01', DATE '2024-01-31'),
    (105.00, 96.0, 604, 18.4, 'EQE SUV 500 4MATIC (MY23-24)', '500 4MATIC', DATE '2023-10-01', DATE '2025-04-30'),
    (105.00, 96.0, 597, 19.3, 'EQE SUV 500 4MATIC (MY25)',    '500 4MATIC', DATE '2025-04-01', NULL)
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQE_SUV' AND x.variant_name = v.variante);

-- Zeile '94/94 kWh': kein echtes EQE-SUV-Modell, aber ein Auto haengt daran. Das Auto wird
-- nicht angefasst. Die Zeile bekommt nur ein trim_level, sonst blendet das Formular sie neben
-- den Trim-Gruppen aus. Bewusst ohne Zeitraum.
UPDATE vehicle_specification
SET trim_level = 'Sonstige', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQE_SUV' AND variant_name = '94/94 kWh'
  AND trim_level IS NULL;

-- ============================================================
-- 6. EQV
-- ============================================================
-- Alt-Zeile 90.0 (418 km): daran haengt ein 2022er "300 lang" -> EQV 300 Lang (2020-2024), car/1240
UPDATE vehicle_specification
SET battery_capacity_kwh = 100.00, net_battery_capacity_kwh = 90.0,
    official_range_km = 363, official_consumption_kwh_per_100km = 27.6,
    variant_name = 'EQV 300 Lang (2020-2024)', trim_level = '300 Lang',
    available_from = '2020-09-01', available_to = '2024-01-31', updated_at = NOW()
WHERE car_brand = 'MERCEDES' AND car_model = 'EQV'
  AND battery_capacity_kwh = 90.00 AND variant_name = '';

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, trim_level, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'MERCEDES', 'EQV', v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    ( 66.00, 60.0, 236, 28.0, 'EQV 250 Lang (2021-2024)',      '250 Lang',      DATE '2021-10-01', DATE '2024-01-31'),
    ( 66.00, 60.0, 236, 27.4, 'EQV 250 Lang (2024-2026)',      '250 Lang',      DATE '2024-02-01', DATE '2026-07-31'),
    (100.00, 90.0, 363, 27.6, 'EQV 300 Lang (2024-2026)',      '300 Lang',      DATE '2024-02-01', DATE '2026-07-31'),
    (100.00, 90.0, 361, 27.5, 'EQV 300 Extralang (2024-2026)', '300 Extralang', DATE '2024-07-01', DATE '2026-07-31')
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'MERCEDES' AND x.car_model = 'EQV' AND x.variant_name = v.variante);

-- ============================================================
-- 7. V186-Zeilen nachdatieren (Monate inzwischen von ev-database gelesen)
-- ============================================================
UPDATE vehicle_specification SET available_from = v.ab, updated_at = NOW()
FROM (VALUES
    ('CLA 250 Shooting Brake',        DATE '2026-04-01'),
    ('CLA 250+ Shooting Brake',       DATE '2025-11-01'),
    ('CLA 350 4MATIC Shooting Brake', DATE '2025-11-01'),
    ('GLB 250+',                      DATE '2025-12-01'),
    ('GLB 350 4MATIC',                DATE '2025-12-01'),
    ('G 580 (2024-)',                 DATE '2024-04-01')
) AS v(variante, ab)
WHERE vehicle_specification.car_brand = 'MERCEDES'
  AND vehicle_specification.variant_name = v.variante
  AND vehicle_specification.available_from IS NULL;
