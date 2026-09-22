-- V184: XPeng G9 AWD Performance ergaenzen + trim_level fuer G9-Varianten setzen
--
-- Kontext: Analog zu V162 (G6) fehlte die AWD-Performance-Variante des G9 komplett.
-- AWD Performance nutzt in beiden Generationen denselben Akku wie die jeweilige
-- RWD Long Range (identische battery_capacity_kwh), nur Reichweite/Verbrauch
-- unterscheiden sich durch den Allrad. Daher trim_level als Unterscheidung setzen,
-- damit der kWh-only-Fallback (pickBestMatch) RWD und AWD trennen kann.
--
-- Quellen (ev-database.org, gegengeprueft; Verbrauch = WLTP rated inkl. Ladeverluste,
-- wie bei den bestehenden XPeng-Zeilen):
--   AWD Performance MY24 (2024-2025): https://ev-database.org/car/1826/XPENG-G9-AWD-Performance
--     98.0 kWh nominal / 93.1 kWh nutzbar, NMC, WLTP 520 km, 213 Wh/km, 405 kW
--   AWD Performance MY25 (2026-):     https://ev-database.org/car/3279/XPENG-G9-AWD-Performance
--     93.1 kWh nominal / 92.2 kWh nutzbar, LFP, WLTP 540 km, 201 Wh/km, 423 kW
--   battery_capacity_kwh folgt der jeweiligen Long-Range-Zeile (98.00 bzw. 92.20),
--   damit die Zuordnung ueber die Kapazitaet konsistent bleibt.

-- ============================================================
-- 1) trim_level fuer bestehende G9-Zeilen setzen (idempotent)
-- ============================================================
UPDATE vehicle_specification SET trim_level = 'Long Range RWD (2024-2025)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G9' AND battery_capacity_kwh = 98.00
  AND variant_name = 'Long Range (2024-2025)' AND trim_level IS NULL;

UPDATE vehicle_specification SET trim_level = 'Long Range RWD (2026-)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G9' AND battery_capacity_kwh = 92.20
  AND variant_name = 'Long Range (2026-)' AND trim_level IS NULL;

-- ============================================================
-- 2) AWD Performance ergaenzen
-- ============================================================
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, trim_level,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'XPENG', 'XPENG_G9', 98.00, 93.10, 520, 21.30, 'COMBINED', 'WLTP',
 'AWD Performance (2024-2025)', 'AWD Performance (2024-2025)', NOW(), NOW()),
(gen_random_uuid(), 'XPENG', 'XPENG_G9', 92.20, 92.20, 540, 20.10, 'COMBINED', 'WLTP',
 'AWD Performance (2026-)', 'AWD Performance (2026-)', NOW(), NOW())
ON CONFLICT ON CONSTRAINT uq_vehicle_spec DO NOTHING;
