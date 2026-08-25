-- V154: Tesla - Dubletten korrigieren und fehlende Varianten ergaenzen
--
-- Befund 1 (Dubletten Model Y):
--   V102 hat available_from/to per `battery_capacity_kwh = 75.00` gesetzt. Zu dem
--   Zeitpunkt gab es vier Rows mit 75.00 kWh (LR AWD, LR AWD Juniper, LR RWD,
--   LR RWD Juniper), alle bekamen dadurch 2022-01-01..2025-01-31. Im UI erscheinen
--   dadurch pro Trim zwei identisch beschriftete Zeitraum-Buttons ("01/22-01/25"),
--   und Long Range RWD endet faelschlich im Januar 2025.
--   Die drei Rows mit created_at 2026-04-21 13:02 stammen aus keiner Migration
--   (Flyway lief an dem Tag um 12:06 und 15:17) - sie wurden manuell auf Prod
--   eingefuegt. Deshalb sind alle UPDATEs hier defensiv per variant_name.
--
-- Befund 2 (fehlende Varianten): Model Y Juniper LR RWD, die Premium-Umbenennung
--   ab Okt/Nov 2025 (Model Y), Model X Plaid komplett, Model S/X MY26-Refresh.
--
-- Befund 3: MODEL_3-Row ohne trim_level ('82/79 kWh', 750 km WLTP) ist Datenmuell,
--   750 km hat kein Model 3. Keine Autos verknuepft.
--
-- Quelle: ev-database.org, August 2026 (car/2186, /3120, /3476, /3333, /3362,
--   /1405, /3384, /3383, /1408, /3386, /3385)
--   Verbrauch = "rated consumption" (Herstellerangabe inkl. Ladeverluste).
-- Zeitraeume fuer die Model S/X Altbestaende stammen aus den Jahreszahlen im
--   bestehenden variant_name, nicht aus einer externen Quelle.

-- ============================================================
-- 1. Model Y: LR RWD entzerren
-- ============================================================

-- 1a. 'Model Y LR RWD' = Vor-Facelift LR RWD (ev-database car/2186)
--     78.1 kWh brutto / 75 netto, 600 km, 155 Wh/km, Feb 2024 - Jan 2025
UPDATE vehicle_specification
SET battery_capacity_kwh               = 78.10,
    net_battery_capacity_kwh           = 75.0,
    official_range_km                  = 600,
    official_consumption_kwh_per_100km = 15.5,
    variant_name                       = 'Model Y LR RWD (2024-2025)',
    available_from                     = '2024-02-01',
    available_to                       = '2025-01-31',
    updated_at                         = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name = 'Model Y LR RWD';

-- 1b. 'Model Y LR RWD Juniper' = echtes Juniper LR RWD (ev-database car/3120)
--     78.1 kWh brutto / 75 netto, 622 km, 142 Wh/km, Feb 2025 - Okt 2025
--     Schliesst die Luecke, wegen der die Auswahl bisher im Januar 2025 endete.
UPDATE vehicle_specification
SET battery_capacity_kwh               = 78.10,
    net_battery_capacity_kwh           = 75.0,
    official_range_km                  = 622,
    official_consumption_kwh_per_100km = 14.2,
    variant_name                       = 'Model Y LR RWD Juniper (2025)',
    available_from                     = '2025-02-01',
    available_to                       = '2025-10-31',
    updated_at                         = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name = 'Model Y LR RWD Juniper';

-- ============================================================
-- 2. Model Y: LR AWD entzerren
-- ============================================================

-- 2a. 'Model Y LR AWD Juniper' (455 km, 2022-2025) ist eine Dublette zur bereits
--     korrekten 'Model Y LR AWD Juniper (2025+)' aus V102. Verknuepfte Autos
--     umhaengen, dann Row entfernen.
UPDATE car SET vehicle_specification_id = (
        SELECT id FROM vehicle_specification
        WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
          AND variant_name = 'Model Y LR AWD Juniper (2025+)')
WHERE vehicle_specification_id IN (
    SELECT id FROM vehicle_specification
    WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
      AND variant_name = 'Model Y LR AWD Juniper')
  AND EXISTS (SELECT 1 FROM vehicle_specification
              WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
                AND variant_name = 'Model Y LR AWD Juniper (2025+)');

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name = 'Model Y LR AWD Juniper'
  AND NOT EXISTS (SELECT 1 FROM car c WHERE c.vehicle_specification_id = vehicle_specification.id);

-- 2b. LR AWD Juniper endet mit der Premium-Umbenennung im Oktober 2025.
--     Juniper laeuft weiter, nur die Ausfuehrung wechselt: Tesla hat im Herbst 2025
--     auf Standard/Premium/Performance umbenannt und dabei den Akku getauscht
--     (78.1 kWh brutto -> 82 kWh brutto, LG 5M). Die Premium-Rows unten tragen
--     'Juniper' im Namen, damit die Generation erkennbar bleibt.
UPDATE vehicle_specification
SET available_to = '2025-09-30', updated_at = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name = 'Model Y LR AWD Juniper (2025+)';

-- 2c. Standard Range: Vorgaenger endet mit dem Nachfolger ab Oktober 2025,
--     sonst ueberlappen sich beide Zeitraeume um einen Monat.
UPDATE vehicle_specification
SET available_to = '2025-09-30', updated_at = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name = 'Model Y RWD Juniper';

-- ============================================================
-- 3. Model Y: Premium-Lineup ab Okt/Nov 2025 + Standard ab Okt 2025
-- ============================================================

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'TESLA', 'MODEL_Y', v.brutto, v.netto, v.rw, v.verbrauch,
       'COMBINED', 'WLTP', v.variante, v.trim, v.ab, NULL, NOW(), NOW()
FROM (VALUES
    -- Premium AWD (Juniper), ev-database car/3333, ab Okt 2025
    (82.00, 79.0, 629, 14.9, 'Model Y Premium AWD Juniper', 'Long Range AWD', DATE '2025-10-01'),
    -- Premium RWD (Juniper, LG 5M), ev-database car/3476, ab Nov 2025
    (82.00, 79.0, 661, 14.2, 'Model Y Premium RWD Juniper', 'Long Range RWD', DATE '2025-11-01'),
    -- Standard / RWD (Juniper), ev-database car/3362, ab Okt 2025
    (64.00, 60.0, 534, 13.1, 'Model Y RWD Juniper (2025-2026)', 'Standard Range', DATE '2025-10-01')
) AS v(brutto, netto, rw, verbrauch, variante, trim, ab)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'TESLA' AND x.car_model = 'MODEL_Y' AND x.variant_name = v.variante);

-- ============================================================
-- 4. Model S / Model X: Zeitraeume nachtragen + MY26-Refresh + Plaid
-- ============================================================

-- 4a. Altbestaende datieren, damit sie neben den MY26-Rows sichtbar bleiben
--     (das UI blendet undatierte Optionen aus, sobald eine Gruppe datierte hat).
--     Jahreszahlen stammen aus dem jeweiligen variant_name.
UPDATE vehicle_specification SET available_from = v.ab, available_to = v.bis, updated_at = NOW()
FROM (VALUES
    ('Model S 85 (2013-2016)',                    DATE '2013-01-01', DATE '2016-12-31'),
    ('Model S 75D (2016-2019)',                   DATE '2016-01-01', DATE '2019-12-31'),
    ('Model S Performance (2019-2021)',           DATE '2019-01-01', DATE '2021-12-31'),
    ('Model S 100D / Dual Motor (2017-2025)',     DATE '2017-01-01', DATE '2025-07-31'),
    ('Model S Plaid (2021-2025)',                 DATE '2021-01-01', DATE '2025-07-31'),
    ('Model X Dual Motor / Long Range (2019-2025)', DATE '2019-01-01', DATE '2025-07-31')
) AS v(variante, ab, bis)
WHERE vehicle_specification.car_brand = 'TESLA'
  AND vehicle_specification.variant_name = v.variante;

-- 4b. Model S Plaid: 396 km war falsch (ev-database car/1405: 600 km, 187 Wh/km)
UPDATE vehicle_specification
SET official_range_km = 600, official_consumption_kwh_per_100km = 18.7, updated_at = NOW()
WHERE car_brand = 'TESLA' AND variant_name = 'Model S Plaid (2021-2025)';

-- 4c. Model X 90D hatte kein trim_level und fiel damit aus der Trim-Gruppierung
UPDATE vehicle_specification SET trim_level = 'Dual Motor', updated_at = NOW()
WHERE car_brand = 'TESLA' AND variant_name = 'Model X 90D (2015-2016)' AND trim_level IS NULL;

-- 4d. Neue Model S / Model X Varianten
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'TESLA', v.modell, v.brutto, v.netto, v.rw, v.verbrauch,
       'COMBINED', 'WLTP', v.variante, v.trim, v.ab, v.bis, NOW(), NOW()
FROM (VALUES
    -- Model X Plaid fehlte komplett (ev-database car/1408)
    ('MODEL_X', 100.00, 95.0, 543, 20.8, 'Model X Plaid (2022-2025)', 'Plaid',      DATE '2022-11-01', DATE '2025-07-31'),
    -- MY26-Refresh, Bestellstart Oktober 2025 (car/3386, /3385, /3384, /3383)
    ('MODEL_X', 100.00, 95.0, 567, 19.3, 'Model X Plaid (MY26)',      'Plaid',      DATE '2025-10-01', NULL),
    ('MODEL_X', 100.00, 95.0, 600, 18.3, 'Model X AWD (MY26)',        'Dual Motor', DATE '2025-10-01', NULL),
    ('MODEL_S', 100.00, 95.0, 611, 18.0, 'Model S Plaid (MY26)',      'Plaid',      DATE '2025-10-01', NULL),
    ('MODEL_S', 100.00, 95.0, 744, 14.8, 'Model S AWD (MY26)',        'Dual Motor', DATE '2025-10-01', NULL)
) AS v(modell, brutto, netto, rw, verbrauch, variante, trim, ab, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'TESLA' AND x.car_model = v.modell AND x.variant_name = v.variante);

-- ============================================================
-- 5. Datenmuell entfernen (keine Autos verknuepft)
-- ============================================================
DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3' AND variant_name = '82/79 kWh'
  AND NOT EXISTS (SELECT 1 FROM car c WHERE c.vehicle_specification_id = vehicle_specification.id);

-- ============================================================
-- 6. Model 3: Einstiegsvariante ab Dezember 2025 ergaenzen
-- ============================================================
-- Der bestehende 'Model 3 RWD Highland' (60 kWh, seit 05/25) ist die CATL-6M-
-- Version, die im Dezember 2025 vom groesseren 64-kWh-Pack abgeloest wurde
-- (ev-database car/3186 bzw. car/3403). Ohne Nachfolger endet Standard Range
-- offen, obwohl die Ausfuehrung nicht mehr gebaut wird.
UPDATE vehicle_specification
SET available_to = '2025-11-30', updated_at = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 RWD Highland'
  AND available_to IS NULL;

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'TESLA', 'MODEL_3', 64.00, 60.0, 534, 13.0,
       'COMBINED', 'WLTP', 'Model 3 RWD Highland (2025-2026)', 'Standard Range',
       DATE '2025-12-01', NULL, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification x
    WHERE x.car_brand = 'TESLA' AND x.car_model = 'MODEL_3'
      AND x.variant_name = 'Model 3 RWD Highland (2025-2026)');

-- 6b. Model 3 RWD Highland: Reichweite 445 -> 513 km.
--     Quelle: Wikipedia. Akkukapazitaet bleibt bei 60 kWh, Verbrauch unveraendert.
UPDATE vehicle_specification
SET official_range_km = 513, updated_at = NOW()
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 RWD Highland';
