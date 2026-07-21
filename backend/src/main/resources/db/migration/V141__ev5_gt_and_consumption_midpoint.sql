-- EV5: GT-Variante ergaenzen + 2WD-Verbrauch auf WLTP-Mittelwert korrigieren.
-- Quelle: Wikipedia/Kia (GT seit 03/2026: 476 km, 18.6 kWh/100km, 225 kW/306 PS, Allrad).
-- GT teilt den 81.4-kWh-NMC-Akku (78.0 netto) mit 2WD/AWD, unterschieden via trim_level.
--
-- available_from bleibt NULL, damit der by-Kapazitaet-Fallback (Alt-Autos ohne Spec-Link) die
-- 2WD-Basis und nicht die GT waehlt (2WD behaelt available_from aus V137). Exakte Auswahl
-- laeuft ueber Trim + Spec-Link.
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'KIA', 'EV_5', 81.40, 78.00, 476, 18.6, 'COMBINED', 'WLTP',
 'EV5 81.4 kWh GT (2026-)', 'GT', NULL, NULL, NOW(), NOW());

-- 2WD-Verbrauch auf WLTP-Mittelwert: 16.9-17.8 -> 17.35 (war 16.9 aus V137). AWD 18.1 ist
-- Einzelwert und bleibt.
UPDATE vehicle_specification SET official_consumption_kwh_per_100km = 17.35
WHERE car_model = 'EV_5' AND variant_name = 'EV5 81.4 kWh 2WD (2025-)';
