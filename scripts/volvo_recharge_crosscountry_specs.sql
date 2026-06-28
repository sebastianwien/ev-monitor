-- Volvo: Legacy-Namen (XC40/C40 Recharge) + EX30 Cross Country Single Motor
-- Quelle: ADAC Autokatalog (WLTP kombiniert, inkl. Ladeverluste), verifiziert 2026-06.
-- Konvention: battery_capacity_kwh = Brutto, net_battery_capacity_kwh = Netto nutzbar.
-- Voraussetzung: CarBrand.CarModel kennt XC_40_RECHARGE, C_40_RECHARGE, EX_30_CROSS_COUNTRY.
--
-- Ausfuehren auf PROD:
--   docker exec -i ev-monitor-db-1 psql -U evmonitor ev_monitor < volvo_recharge_crosscountry_specs.sql
--
-- Idempotent: ON CONFLICT DO NOTHING.
-- Hinweis: XC40/C40 Recharge Twin Motor blieb bei 78 kWh (die 82-kWh-Twin ist der EX40/EC40).
--          Pro Variante eine repraesentative (spaete) WLTP-Zeile; Netto-Kapazitaet ist jahresunabhaengig.

BEGIN;

INSERT INTO vehicle_specification (
    id, car_brand, car_model, variant_name,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, created_at, updated_at
)
VALUES
    -- EX30 Cross Country: Single Motor RWD (P5 Long Range, ab 04/26) - ergaenzt die bereits vorhandene Twin Motor Performance
    (gen_random_uuid(), 'VOLVO', 'EX_30_CROSS_COUNTRY', 'Single Motor', 69.00, 65.00, 463.00, 17.10, 'COMBINED', 'WLTP', now(), now()),

    -- XC40 Recharge (2020-2024, vor Rename zu EX40)
    (gen_random_uuid(), 'VOLVO', 'XC_40_RECHARGE', 'Single Motor',                69.00, 66.00, 478.00, 16.60, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'XC_40_RECHARGE', 'Twin Motor',                  78.00, 75.00, 418.00, 23.80, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'XC_40_RECHARGE', 'Single Motor Extended Range', 82.00, 79.00, 575.00, 16.60, 'COMBINED', 'WLTP', now(), now()),

    -- C40 Recharge (2021-2024, vor Rename zu EC40)
    (gen_random_uuid(), 'VOLVO', 'C_40_RECHARGE', 'Single Motor',                69.00, 67.00, 423.00, 18.70, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'C_40_RECHARGE', 'Twin Motor',                  78.00, 75.00, 444.00, 20.70, 'COMBINED', 'WLTP', now(), now()),
    (gen_random_uuid(), 'VOLVO', 'C_40_RECHARGE', 'Single Motor Extended Range', 82.00, 79.00, 575.00, 16.60, 'COMBINED', 'WLTP', now(), now())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, variant_name, wltp_type, rating_source) DO NOTHING;

COMMIT;
