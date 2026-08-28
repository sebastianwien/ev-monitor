-- V159: XPeng Variantennamen setzen + G9-Duplikat konsolidieren + P7+-Altlasten entfernen
--
-- Kontext: Onboarding-Wizard zeigte beim G6 drei leere Varianten-Buttons, weil alle
-- XPeng-Zeilen variant_name = '' (V90-Sentinel) trugen. Die Zeilen fuer G9/P7/P7+
-- existierten bisher nur als manuell eingefuegte Prod-Zeilen ohne Migration (Drift);
-- diese Migration benennt sie und legt sie idempotent auch lokal an.
--
-- Quellen (gegengeprueft):
--   G6 MY24/25: https://evkx.net/models/xpeng/g6/ - RWD Standard Range 66 kWh / 435 km,
--               RWD Long Range 87.5 kWh NMC / 570 km (AWD Performance 550 km: nicht in DB)
--   G6 MY26:    https://insideevs.de/news/765526/xpeng-g6-g9-2025-preise/ - Facelift:
--               Long Range 80 kWh LFP / 525 km ersetzt den 87.5-NMC-Pack
--   G9 MY24/25: 98 kWh brutto / 570 km Long Range
--   G9 MY26:    https://www.electrive.com/2026/03/26/testing-the-xpeng-g9-performance-the-learning-curve-is-steep/
--               - Facelift Long Range: 92.2 kWh netto LFP (vorher 93.1 NMC), 585 km
--   P7:         https://ev-database.org/car/1821 - RWD Long Range 82.7 kWh / 576 km
--   P7+ MY26:   https://www.electrive.com/2026/01/12/xpeng-p7-id-7-rival-launches-in-europe-at-e46600/
--               + https://ev-database.org/car/3432 - Long Range 76.3 brutto / 74.9 netto, 530 km
--
-- Namenskonvention: Jahres-Suffix nur dort, wo zwei Generationen derselben Variante
-- kollidieren (G6/G9 Long Range). Das 80-kWh-G6-Pack deckt auch die chinesische
-- Facelift-Variante ab (vgl. V28), Datenwerte der Zeile sind EU-WLTP.

-- ============================================================
-- 1) Variantennamen setzen (idempotent: nur unbenannte Zeilen)
-- ============================================================
UPDATE vehicle_specification SET variant_name = 'Standard Range (2024-2025)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 66.00
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range (2024-2025)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 87.50
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range (2026-)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 80.00
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range (2024-2025)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G9' AND battery_capacity_kwh = 98.00
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range (2026-)',
       net_battery_capacity_kwh = 92.20, updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G9' AND battery_capacity_kwh = 92.20
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_P7' AND battery_capacity_kwh = 82.70
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

UPDATE vehicle_specification SET variant_name = 'Long Range',
       net_battery_capacity_kwh = 74.90, updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_P7_PLUS' AND battery_capacity_kwh = 76.30
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP' AND variant_name = '';

-- ============================================================
-- 2) G9-Duplikat konsolidieren: 93.1-Zeile (netto 92.2, 585 km) und 92.2-Zeile
--    beschreiben dieselbe Facelift-Variante. Kanonisch bleibt 92.20 (Marketing-/
--    Netto-Wert). Autos umhaengen, dann Duplikat loeschen.
-- ============================================================
UPDATE car c SET vehicle_specification_id = tgt.id
FROM vehicle_specification src, vehicle_specification tgt
WHERE c.vehicle_specification_id = src.id
  AND src.car_brand = 'XPENG' AND src.car_model = 'XPENG_G9' AND src.battery_capacity_kwh = 93.10
  AND src.wltp_type = 'COMBINED' AND src.rating_source = 'WLTP'
  AND tgt.car_brand = 'XPENG' AND tgt.car_model = 'XPENG_G9' AND tgt.battery_capacity_kwh = 92.20
  AND tgt.wltp_type = 'COMBINED' AND tgt.rating_source = 'WLTP';

DELETE FROM vehicle_specification
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G9' AND battery_capacity_kwh = 93.10
  AND wltp_type = 'COMBINED' AND rating_source = 'WLTP';

-- ============================================================
-- 3) P7+-Altlasten: '75/73 kWh' und '75/74 kWh' entsprechen keiner offiziellen
--    EU-Variante (Standard Range = 61.7 kWh / 455 km). Nur loeschen, wenn kein
--    Auto verknuepft ist.
-- ============================================================
DELETE FROM vehicle_specification vs
WHERE vs.car_brand = 'XPENG' AND vs.car_model = 'XPENG_P7_PLUS' AND vs.battery_capacity_kwh = 75.00
  AND NOT EXISTS (SELECT 1 FROM car c WHERE c.vehicle_specification_id = vs.id);

-- ============================================================
-- 4) Drift beheben: Zeilen, die bisher nur manuell auf Prod existierten,
--    auch lokal anlegen (auf Prod via Unique-Constraint uebersprungen)
-- ============================================================
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'XPENG', 'XPENG_G9', 98.00, 93.60, 570, 19.4, 'COMBINED', 'WLTP',
 'Long Range (2024-2025)', NOW(), NOW()),
(gen_random_uuid(), 'XPENG', 'XPENG_G9', 92.20, 92.20, 585, 18.6, 'COMBINED', 'WLTP',
 'Long Range (2026-)', NOW(), NOW()),
(gen_random_uuid(), 'XPENG', 'XPENG_P7', 82.70, 82.70, 576, 16.8, 'COMBINED', 'WLTP',
 'Long Range', NOW(), NOW()),
(gen_random_uuid(), 'XPENG', 'XPENG_P7_PLUS', 76.30, 74.90, 530, 16.5, 'COMBINED', 'WLTP',
 'Long Range', NOW(), NOW())
ON CONFLICT ON CONSTRAINT uq_vehicle_spec DO NOTHING;
