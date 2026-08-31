-- V162: XPeng G6 AWD Performance ergaenzen + trim_level fuer G6-Varianten setzen
--
-- Kontext: G6 AWD Performance fehlte komplett in vehicle_specification (in V159 nur als
-- Kommentar vermerkt: "AWD Performance 550 km: nicht in DB"). AWD Performance nutzt in
-- beiden Generationen denselben Akku wie die jeweilige RWD Long Range (identische
-- battery_capacity_kwh) - nur Reichweite/Verbrauch unterscheiden sich durch den Allrad.
-- Ohne Unterscheidung wuerde der kWh-only-Fallback (pickBestMatch in
-- PostgresVehicleSpecificationRepositoryImpl) bei Autos ohne explizite
-- vehicle_specification_id nicht zuverlaessig zwischen RWD und AWD trennen koennen.
-- Daher trim_level (V93) fuer alle vier betroffenen G6-Zeilen setzen.
--
-- Quellen (ev-database.org, gegengeprueft):
--   AWD Performance MY24 (2024-2025): https://ev-database.org/car/2183/XPENG-G6-AWD-Performance
--     87.5 kWh nominal / 92.0 kWh brutto, NMC, WLTP 550 km, 179 Wh/km (17.9 kWh/100km)
--   AWD Performance MY25 (2026-):     https://ev-database.org/car/3276/XPENG-G6-AWD-Performance
--     80.0 kWh nominal / 80.8 kWh brutto, LFP, WLTP 510 km, 184 Wh/km (18.4 kWh/100km)

-- ============================================================
-- 1) trim_level fuer bestehende Zeilen setzen (idempotent)
-- ============================================================
UPDATE vehicle_specification SET trim_level = 'Standard Range', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 66.00
  AND trim_level IS NULL;

UPDATE vehicle_specification SET trim_level = 'Long Range RWD (2024-2025)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 87.50
  AND trim_level IS NULL;

UPDATE vehicle_specification SET trim_level = 'Long Range RWD (2026-)', updated_at = NOW()
WHERE car_brand = 'XPENG' AND car_model = 'XPENG_G6' AND battery_capacity_kwh = 80.00
  AND trim_level IS NULL;

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
(gen_random_uuid(), 'XPENG', 'XPENG_G6', 87.50, 84.00, 550, 17.90, 'COMBINED', 'WLTP',
 'AWD Performance (2024-2025)', 'AWD Performance (2024-2025)', NOW(), NOW()),
(gen_random_uuid(), 'XPENG', 'XPENG_G6', 80.00, 80.00, 510, 18.40, 'COMBINED', 'WLTP',
 'AWD Performance (2026-)', 'AWD Performance (2026-)', NOW(), NOW())
ON CONFLICT ON CONSTRAINT uq_vehicle_spec DO NOTHING;
