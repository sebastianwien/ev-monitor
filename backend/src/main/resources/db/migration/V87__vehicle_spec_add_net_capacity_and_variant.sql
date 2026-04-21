-- V87: vehicle_specification bekommt variant_name + net_battery_capacity_kwh
-- battery_capacity_kwh bleibt als Lookup-Key (nominal/brutto, matcht car.battery_capacity_kwh)
-- net_battery_capacity_kwh = verifizierter Netto-Wert (ev-database.org)

ALTER TABLE vehicle_specification
    ADD COLUMN variant_name             VARCHAR(100),
    ADD COLUMN net_battery_capacity_kwh NUMERIC(10,2);

-- ============================================================
-- GARBAGE löschen
-- ============================================================
DELETE FROM vehicle_specification WHERE car_model = 'IONIQ_5'  AND battery_capacity_kwh = 7.00;
DELETE FROM vehicle_specification WHERE car_model = 'MODEL_Y'  AND battery_capacity_kwh = 8.00;
DELETE FROM vehicle_specification WHERE car_model = 'MODEL_Y'  AND battery_capacity_kwh = 85.00;
DELETE FROM vehicle_specification WHERE car_model = 'IONIQ_5'  AND battery_capacity_kwh = 58.00 AND car_brand = 'HYUNDAI'; -- IONIQ_5 SR 2WD MY21: net=54, nominal=58 → kein User matcht darauf
DELETE FROM vehicle_specification WHERE car_model = 'DACIA_SPRING' AND battery_capacity_kwh = 33.00; -- Brutto; 26.8-Eintrag korrekt
-- E_UP 36.80 behalten als Lookup-Key (User haben Brutto-Wert eingetragen), net=32.3
UPDATE vehicle_specification SET net_battery_capacity_kwh = 32.3, variant_name = 'e-up! Brutto-Match (2019-2023)'
WHERE car_brand = 'VW' AND car_model = 'E_UP' AND battery_capacity_kwh = 36.80;

-- ============================================================
-- BMW I3 - alle drei Einträge: nominal war 22/33/42.2, net ist 18.8/27.2/37.9
-- Quelle: ev-database.org/car/1004, /1068, /1145
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 18.8, variant_name = 'i3 60 Ah (2013-2017)'
WHERE car_brand = 'BMW' AND car_model = 'I3' AND battery_capacity_kwh = 22.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 27.2, variant_name = 'i3 94 Ah (2016-2018)'
WHERE car_brand = 'BMW' AND car_model = 'I3' AND battery_capacity_kwh = 33.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 37.9, variant_name = 'i3 120 Ah (2018-2022)'
WHERE car_brand = 'BMW' AND car_model = 'I3' AND battery_capacity_kwh = 42.20;

-- ============================================================
-- Audi E_TRON - nominal 71/95, net 64.7/86.5
-- Quelle: ev-database.org/car/1209, /1355
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 64.7, variant_name = 'e-tron 50 quattro (2019-2022)'
WHERE car_brand = 'AUDI' AND car_model = 'E_TRON' AND battery_capacity_kwh = 71.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 86.5, variant_name = 'e-tron 55 quattro (2019-2022)'
WHERE car_brand = 'AUDI' AND car_model = 'E_TRON' AND battery_capacity_kwh = 95.00;

-- ============================================================
-- CUPRA Born - 78 kWh nominal → 77 kWh net
-- Quelle: ev-database.org/car/1518
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Born 170 kW 77 kWh (2022-2024)'
WHERE car_brand = 'CUPRA' AND car_model = 'CUPRA_BORN' AND battery_capacity_kwh = 78.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 58.0, variant_name = 'Born 150 kW 58 kWh (2021-2024)'
WHERE car_brand = 'CUPRA' AND car_model = 'CUPRA_BORN' AND battery_capacity_kwh = 58.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Tavascan Endurance (2024-2026)'
WHERE car_brand = 'CUPRA' AND car_model = 'CUPRA_TAVASCAN' AND battery_capacity_kwh = 77.00;

-- Zusätzlicher Lookup-Eintrag: User die korrekten Netto-Wert 77 eingetragen haben
INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at, variant_name, net_battery_capacity_kwh)
VALUES (gen_random_uuid(), 'CUPRA', 'CUPRA_BORN', 77.00, 570.00, 15.60, 'COMBINED', 'WLTP', NOW(), NOW(), 'Born 170 kW 77 kWh (2022-2024)', 77.0);

-- ============================================================
-- Fiat 500e - 42 kWh nominal → 37.3 kWh net
-- Quelle: ev-database.org Cheatsheet
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 21.3, variant_name = '500e 24 kWh (2020-2024)'
WHERE car_brand = 'FIAT' AND car_model = 'FIAT_500E' AND battery_capacity_kwh = 21.30;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 37.3, variant_name = '500e 42 kWh (2020-2024)'
WHERE car_brand = 'FIAT' AND car_model = 'FIAT_500E' AND battery_capacity_kwh = 42.00;

-- ============================================================
-- Hyundai IONIQ 5 - alle Einträge: nominal → net
-- Quelle: ev-database.org/car/1478, /1662, /1663, /2236, /2044
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 70.0, variant_name = 'IONIQ 5 LR 2WD MY21 (2021-2022)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_5' AND battery_capacity_kwh = 72.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 74.0, variant_name = 'IONIQ 5 LR 2WD MY22 (2022-2024)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_5' AND battery_capacity_kwh = 72.60;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 74.0, variant_name = 'IONIQ 5 LR AWD MY22 (2022-2024)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_5' AND battery_capacity_kwh = 77.40;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 80.0, variant_name = 'IONIQ 5 84 kWh RWD MY24 (2024-2026)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_5' AND battery_capacity_kwh = 84.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 80.0, variant_name = 'IONIQ 5 N (2023-2026)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_5_N' AND battery_capacity_kwh = 80.00;

-- ============================================================
-- Hyundai IONIQ 6 - nominal → net
-- Quelle: ev-database.org/car/1717, /1718, /1719
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 50.0, variant_name = 'IONIQ 6 Standard Range 2WD (2023-2026)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_6' AND battery_capacity_kwh = 53.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 74.0, variant_name = 'IONIQ 6 LR 2WD (2022-2026)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'IONIQ_6' AND battery_capacity_kwh = 77.40;

-- 63 kWh und 84 kWh Eintraege bleiben vorerst ohne net_battery_capacity_kwh (unverified)

-- Kona Electric - confirmed OK per ev-database.org (net = nominal bei diesen Varianten)
UPDATE vehicle_specification SET net_battery_capacity_kwh = 48.6, variant_name = 'Kona Electric 48.6 kWh (2021-2023)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'KONA_ELECTRIC' AND battery_capacity_kwh = 48.60;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 64.8, variant_name = 'Kona Electric 64.8 kWh (2023-2024)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'KONA_ELECTRIC' AND battery_capacity_kwh = 64.80;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 65.4, variant_name = 'Kona Electric 65.4 kWh (2024-2026)'
WHERE car_brand = 'HYUNDAI' AND car_model = 'KONA_ELECTRIC' AND battery_capacity_kwh = 65.40;

-- ============================================================
-- Kia EV6 - nominal → net
-- Quelle: ev-database.org/car/1480, /1481, /1471
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 54.0, variant_name = 'EV6 Standard Range 2WD (2021-2024)'
WHERE car_brand = 'KIA' AND car_model = 'EV_6' AND battery_capacity_kwh = 58.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 74.0, variant_name = 'EV6 Long Range 2WD (2021-2024)'
WHERE car_brand = 'KIA' AND car_model = 'EV_6' AND battery_capacity_kwh = 77.40;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 74.0, variant_name = 'EV6 GT (2022-2024)'
WHERE car_brand = 'KIA' AND car_model = 'EV_6' AND battery_capacity_kwh = 84.00;

-- Kia EV9 - nominal 99.8 → net 96.0
-- Quelle: ev-database.org/car/1834
UPDATE vehicle_specification SET net_battery_capacity_kwh = 96.0, variant_name = 'EV9 99.8 kWh (2023-2026)'
WHERE car_brand = 'KIA' AND car_model = 'EV_9' AND battery_capacity_kwh = 99.80;

-- Kia Niro EV - net = nominal (64.8)
-- Quelle: ev-database.org/car/1666
UPDATE vehicle_specification SET net_battery_capacity_kwh = 64.8, variant_name = 'Niro EV (2022-2025)'
WHERE car_brand = 'KIA' AND car_model = 'NIRO_EV' AND battery_capacity_kwh = 64.80;

-- ============================================================
-- Nissan Leaf - nominal → net
-- Quelle: ev-database.org/car/1656, /uk/car/1144
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 39.0, variant_name = 'LEAF 40 kWh (2018-2024)'
WHERE car_brand = 'NISSAN' AND car_model = 'LEAF' AND battery_capacity_kwh = 40.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 59.0, variant_name = 'LEAF e+ 62 kWh (2019-2024)'
WHERE car_brand = 'NISSAN' AND car_model = 'LEAF' AND battery_capacity_kwh = 62.00;

-- ============================================================
-- Opel - nominal → net
-- Quelle: ev-database.org/car/1051, /1192, /1278
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 58.0, variant_name = 'Ampera-e (2017-2021)'
WHERE car_brand = 'OPEL' AND car_model = 'AMPERA_E' AND battery_capacity_kwh = 60.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 46.3, variant_name = 'Corsa-e (2020-2023)'
WHERE car_brand = 'OPEL' AND car_model = 'CORSA_E' AND battery_capacity_kwh = 50.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 46.3, variant_name = 'Mokka-e (2021-2024)'
WHERE car_brand = 'OPEL' AND car_model = 'MOKKA_E' AND battery_capacity_kwh = 50.00;

-- ============================================================
-- Peugeot e-208 - nominal 50 → net 46.3
-- Quelle: ev-database.org/uk/car/1583
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 46.3, variant_name = 'e-208 (2020-2023)'
WHERE car_brand = 'PEUGEOT' AND car_model = 'E_208' AND battery_capacity_kwh = 50.00;

-- ============================================================
-- Skoda ELROQ
-- 79 kWh = Elroq RS (net=79 laut Cheatsheet) → behalten, variant_name setzen
-- 82 kWh = Elroq 85 Brutto → net=77, variant_name setzen
-- 84 kWh = Elroq RS MY26? → vorerst unverified lassen
-- Fehlende Varianten hinzufügen: 50 (52 kWh) und 60 (59 kWh)
-- Quelle: ev-database.org/car/3031, /3032, /3033, /3372
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 79.0, variant_name = 'Elroq vRS (2025)'
WHERE car_brand = 'SKODA' AND car_model = 'ELROQ' AND battery_capacity_kwh = 79.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Elroq 85 / 85x (2024-2026)'
WHERE car_brand = 'SKODA' AND car_model = 'ELROQ' AND battery_capacity_kwh = 82.00;

-- Elroq 50 hinzufügen
INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at, variant_name, net_battery_capacity_kwh)
VALUES (gen_random_uuid(), 'SKODA', 'ELROQ', 52.00, 377.00, 15.70, 'COMBINED', 'WLTP', NOW(), NOW(), 'Elroq 50 (2024-2026)', 52.00);

-- Elroq 60 hinzufügen
INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at, variant_name, net_battery_capacity_kwh)
VALUES (gen_random_uuid(), 'SKODA', 'ELROQ', 59.00, 429.00, 15.70, 'COMBINED', 'WLTP', NOW(), NOW(), 'Elroq 60 (2024-2026)', 59.00);

-- ============================================================
-- Skoda ENYAQ
-- 62 kWh nominal → net 58 kWh (Enyaq 60 MY21-23)
-- 77 kWh = Enyaq 85 net → OK
-- 82 kWh = Brutto des 85er → net 77 (ist aber Duplikat des 77-Eintrags, daher löschen)
-- 85 kWh → Enyaq RS? unverified, vorerst nur variant_name
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 58.0, variant_name = 'Enyaq iV 60 (2021-2023)'
WHERE car_brand = 'SKODA' AND car_model = 'ENYAQ' AND battery_capacity_kwh = 62.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Enyaq 85 / iV 80 (2021-2026)'
WHERE car_brand = 'SKODA' AND car_model = 'ENYAQ' AND battery_capacity_kwh = 77.00;

-- 82 kWh Enyaq-Eintrag: Nutzer haben 82 eingetragen (Brutto), also Eintrag behalten als Lookup-Key
-- aber net auf 77 setzen
UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Enyaq 85 / iV 80 - Brutto-Match'
WHERE car_brand = 'SKODA' AND car_model = 'ENYAQ' AND battery_capacity_kwh = 82.00;

UPDATE vehicle_specification SET variant_name = 'Enyaq RS (2024-2026)'
WHERE car_brand = 'SKODA' AND car_model = 'ENYAQ' AND battery_capacity_kwh = 85.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'Enyaq Coupe iV 80 (2022-2026)'
WHERE car_brand = 'SKODA' AND car_model = 'ENYAQ_COUPE' AND battery_capacity_kwh = 77.00;

-- Zusätzlicher Lookup-Eintrag: User die Brutto-Wert 82 eingetragen haben (Enyaq Coupe 85 / iV 80x)
INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at, variant_name, net_battery_capacity_kwh)
VALUES (gen_random_uuid(), 'SKODA', 'ENYAQ_COUPE', 82.00, 529.00, 16.50, 'COMBINED', 'WLTP', NOW(), NOW(), 'Enyaq Coupe 85 / iV 80x - Brutto-Match', 77.0);

-- ============================================================
-- Tesla MODEL_S - alte Modelle nominal → net
-- Quelle: ev-database.org/car/1070, /1031, /1088, /1207, /1404, /1405
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 72.5, variant_name = 'Model S 75D (2016-2019)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND battery_capacity_kwh = 75.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 80.8, variant_name = 'Model S 85 (2013-2016)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND battery_capacity_kwh = 85.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 95.0, variant_name = 'Model S 100D / Dual Motor (2017-2025)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND battery_capacity_kwh = 95.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 95.0, variant_name = 'Model S Performance (2019-2021)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S_PERFORMANCE' AND battery_capacity_kwh = 95.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 95.0, variant_name = 'Model S Plaid (2021-2025)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S_PLAID' AND battery_capacity_kwh = 95.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 95.0, variant_name = 'Model X Dual Motor / Long Range (2019-2025)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_X' AND battery_capacity_kwh = 95.00;

-- ============================================================
-- Tesla MODEL_Y
-- 60 kWh: RWD Juniper (net=60) oder CATL LFP (net=57) - beide User-Eingaben matchen 60
-- 75 kWh: LR AWD 2022-2025 (net=75) - korrekt
-- 78.4 kWh: User hat nominal eingetragen, LR RWD 2024-25 hat net=75
-- 79 kWh: Performance 2022-25 (net=75) oder LR AWD Juniper (gross=78.1)
-- 82 kWh: kein klarer Match, net setzen auf 75 (LR-Variante)
-- Quelle: ev-database.org/car/3103, /1619, /1183, /2186
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 60.0, variant_name = 'Model Y RWD Juniper (2025-2026)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y' AND battery_capacity_kwh = 60.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model Y LR AWD (2022-2025)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y' AND battery_capacity_kwh = 75.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model Y LR AWD Juniper - Brutto-Match'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y' AND battery_capacity_kwh = 78.40;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model Y Performance / LR AWD Juniper'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y' AND battery_capacity_kwh = 79.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model Y LR (Brutto-Match)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y' AND battery_capacity_kwh = 82.00;

-- ============================================================
-- Tesla MODEL_3
-- 57.5 kWh: SR+/RWD LFP - net ca. 57 kWh (Tesla gibt nichts offiziell an)
-- 73.5 kWh: LR RWD 2019-2021 - net 73.5 (ev-database.org bestätigt, User-Angabe auch)
-- 75 kWh: LR AWD 2021-2023 - net 75 kWh
-- 79 kWh: Performance alle MY / LR Highland Brutto - net 75 kWh (Performance) / 75 kWh (LR Highland)
-- Quelle: ev-database.org/car/1591, /3034, /2188
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 57.0, variant_name = 'Model 3 SR+/RWD LFP (2020-2023)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3' AND battery_capacity_kwh = 57.50;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 73.5, variant_name = 'Model 3 LR RWD (2019-2021)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3' AND battery_capacity_kwh = 73.50;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model 3 LR AWD (2021-2023)'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3' AND battery_capacity_kwh = 75.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 75.0, variant_name = 'Model 3 Performance / LR Highland'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3' AND battery_capacity_kwh = 79.00;

-- ============================================================
-- VW E_UP - 32.3 kWh net korrekt
-- ============================================================
UPDATE vehicle_specification SET net_battery_capacity_kwh = 32.3, variant_name = 'e-up! (2020-2023)'
WHERE car_brand = 'VW' AND car_model = 'E_UP' AND battery_capacity_kwh = 32.30;

-- VW E_GOLF
UPDATE vehicle_specification SET net_battery_capacity_kwh = 32.3, variant_name = 'e-Golf (2017-2020)'
WHERE car_brand = 'VW' AND car_model = 'E_GOLF' AND battery_capacity_kwh = 35.80;

-- VW ID-Reihe (alle bereits korrekt laut Audit, nur variant_name setzen)
UPDATE vehicle_specification SET net_battery_capacity_kwh = 45.0, variant_name = 'ID.3 Pure (2020-2022)'
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 45.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 58.0, variant_name = 'ID.3 Pro (2020-2022)'
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 58.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 59.0, variant_name = 'ID.3 Pro S (2021)'
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 59.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'ID.3 Pro S (2021-2024)'
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 77.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 79.0, variant_name = 'ID.3 Pro S / GTX (2024-2026)'
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 79.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 52.0, variant_name = 'ID.4 Pure (2021-2023)'
WHERE car_brand = 'VW' AND car_model = 'ID_4' AND battery_capacity_kwh = 52.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'ID.4 Pro / GTX (2021-2024)'
WHERE car_brand = 'VW' AND car_model = 'ID_4' AND battery_capacity_kwh = 77.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 82.0, variant_name = 'ID.4 Pro (2024-2026)'
WHERE car_brand = 'VW' AND car_model = 'ID_4' AND battery_capacity_kwh = 82.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'ID.5 Pro / GTX (2022-2024)'
WHERE car_brand = 'VW' AND car_model = 'ID_5' AND battery_capacity_kwh = 77.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 77.0, variant_name = 'ID.7 Pro (2023-2025)'
WHERE car_brand = 'VW' AND car_model = 'ID_7' AND battery_capacity_kwh = 77.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 86.0, variant_name = 'ID.7 Pro S / GTX (2024-2026)'
WHERE car_brand = 'VW' AND car_model = 'ID_7' AND battery_capacity_kwh = 86.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 79.0, variant_name = 'ID. Buzz SWB (2022-2025)'
WHERE car_brand = 'VW' AND car_model = 'ID_BUZZ' AND battery_capacity_kwh = 79.00;

UPDATE vehicle_specification SET net_battery_capacity_kwh = 86.0, variant_name = 'ID. Buzz LWB (2024-2026)'
WHERE car_brand = 'VW' AND car_model = 'ID_BUZZ' AND battery_capacity_kwh = 86.00;

-- Dacia Spring 26.8 kWh korrekt
UPDATE vehicle_specification SET net_battery_capacity_kwh = 26.8, variant_name = 'Spring Electric (2021-2024)'
WHERE car_brand = 'DACIA' AND car_model = 'DACIA_SPRING' AND battery_capacity_kwh = 26.80;

-- Ford Explorer EV 79 kWh korrekt
UPDATE vehicle_specification SET net_battery_capacity_kwh = 79.0, variant_name = 'Explorer Extended Range (2024-2026)'
WHERE car_brand = 'FORD' AND car_model = 'EXPLORER_EV' AND battery_capacity_kwh = 79.00;
