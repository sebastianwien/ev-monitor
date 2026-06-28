-- Volvo Elektro-Modelle: vehicle_specification auffuellen/korrigieren
-- Quelle: ADAC Autokatalog (WLTP kombiniert, inkl. Ladeverluste), verifiziert 2026-06.
-- Konvention: battery_capacity_kwh = Brutto, net_battery_capacity_kwh = Netto nutzbar.
-- Voraussetzung: CarBrand.CarModel kennt EX_30, EX_30_CROSS_COUNTRY, EX_40, EC_40, EX_90, ES_90
--                (sonst sind die Zeilen verwaist - Modell-Liste + SEO-Seiten kommen aus dem Enum).
--
-- Ausfuehren auf PROD:
--   ssh ihle@ev-monitor.net -p 2222
--   docker exec -i ev-monitor-db-1 psql -U evmonitor ev_monitor < volvo_vehicle_specs.sql
--
-- Idempotent: INSERTs via ON CONFLICT DO NOTHING, UPDATE per id.

BEGIN;

-- 1) Bestehende EX_30-Zeile korrigieren.
--    Werte 450 km / 17,5 kWh entsprechen faktisch der Twin Motor Performance;
--    nur der Kapazitaets-Key war falsch (64 -> 69 brutto / 65 netto). Reichweite/Verbrauch bleiben.
UPDATE vehicle_specification
SET battery_capacity_kwh     = 69.00,
    net_battery_capacity_kwh = 65.00,
    variant_name             = 'Twin Motor Performance',
    updated_at               = now()
WHERE id = '71f96f11-6d79-4d0b-89f4-135206a0c253';

-- 2) Fehlende Specs einfuegen.
INSERT INTO vehicle_specification (
    id, car_brand, car_model, variant_name,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, created_at, updated_at
)
VALUES
    -- EX30 (400V)
    (gen_random_uuid(), 'VOLVO', 'EX_30', 'Single Motor',                51.00, 49.00,  339.00, 17.00, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EX_30', 'Single Motor Extended Range', 69.00, 65.00,  476.00, 17.00, 'COMBINED', 'WLTP', now(), now()),

    -- EX30 Cross Country (400V)
    (gen_random_uuid(), 'VOLVO', 'EX_30_CROSS_COUNTRY', 'Twin Motor Performance', 69.00, 65.00, 436.00, 18.30, 'COMBINED', 'WLTP', now(), now()),

    -- EX40 (400V, ex XC40 Recharge)
    (gen_random_uuid(), 'VOLVO', 'EX_40', 'Single Motor',                70.00, 67.00,  477.00, 17.00, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EX_40', 'Single Motor Extended Range', 82.00, 79.00,  576.00, 16.60, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EX_40', 'Twin Motor',                  82.00, 79.00,  540.00, 17.50, 'COMBINED', 'WLTP', now(), now()),

    -- EC40 (400V, ex C40 Recharge)
    (gen_random_uuid(), 'VOLVO', 'EC_40', 'Single Motor',                70.00, 67.00,  486.00, 16.70, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EC_40', 'Single Motor Extended Range', 82.00, 79.00,  585.00, 16.20, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EC_40', 'Twin Motor',                  82.00, 79.00,  554.00, 17.20, 'COMBINED', 'WLTP', now(), now()),

    -- EX90 (400V/800V, MY24/25)
    (gen_random_uuid(), 'VOLVO', 'EX_90', 'Single Motor', 104.00, 100.00, 624.00, 18.10, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'EX_90', 'Twin Motor',   111.00, 107.00, 632.00, 20.30, 'COMBINED', 'WLTP', now(), now()),

    -- ES90 (800V)
    (gen_random_uuid(), 'VOLVO', 'ES_90', 'Single Motor Extended Range', 92.00,  90.00,  662.00, 15.60, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'ES_90', 'Twin Motor',                  106.00, 103.00, 706.00, 16.60, 'COMBINED', 'WLTP', now(), now())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, variant_name, wltp_type, rating_source) DO NOTHING;

COMMIT;

-- Kontrolle:
-- SELECT car_model, variant_name, battery_capacity_kwh, net_battery_capacity_kwh,
--        official_range_km, official_consumption_kwh_per_100km
-- FROM vehicle_specification WHERE car_brand='VOLVO' ORDER BY car_model, battery_capacity_kwh;
