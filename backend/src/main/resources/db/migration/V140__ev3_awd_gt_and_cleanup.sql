-- EV3: AWD- und GT-Variante ergaenzen + manuelle Alt-/Fehl-Zeilen bereinigen.
-- Quelle: Wikipedia/Kia (AWD seit 04/2026: 572 km, 15.8 kWh/100km, 195 kW/265 PS;
--         GT seit 06/2026: 501 km, 18.1 kWh/100km, 215 kW/292 PS). Beide 81.4 kWh NMC / 78.0 netto.
--
-- Bereinigt zwei ueber das Admin-Panel manuell angelegte Zeilen (nicht aus Migrationen):
--   a) Standard-Range-Dublette mit leerem variant_name (58.3/55.0, 414/15.4) - valide Config,
--      aber redundant zur kanonischen V138-Zeile.
--   b) Bogus-Zeile '61/58 kWh' - 61 kWh ist keine reale EV3-Kapazitaet (nur 58.3 oder 81.4).
-- Auf einer frischen DB existieren diese Zeilen nicht -> UPDATE/DELETE sind dort No-Ops.
--
-- Fallback-Determinismus: LR/AWD/GT teilen den Lookup-Key 81.4. pickBestMatch waehlt beim
-- by-Kapazitaet-Fallback (Alt-Autos ohne Spec-Verknuepfung) das spaeteste available_from. Damit
-- die Basis-LR (14.9) und nicht die GT (18.1, zu lenient) gewinnt, bleibt available_from bei
-- AWD/GT bewusst NULL (LR behaelt 2024-09-01). Exakte Auswahl laeuft ueber Trim + Spec-Link.

-- 1) Neue Varianten
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'KIA', 'EV_3', 81.40, 78.00, 572, 15.8, 'COMBINED', 'WLTP',
 'EV3 81.4 kWh AWD (2026-)', 'AWD', NULL, NULL, NOW(), NOW()),
(gen_random_uuid(), 'KIA', 'EV_3', 81.40, 78.00, 501, 18.1, 'COMBINED', 'WLTP',
 'EV3 81.4 kWh GT (2026-)', 'GT', NULL, NULL, NOW(), NOW());

-- 1b) Verbrauch der V138-Zeilen auf WLTP-Mittelwert korrigieren (statt Basis-Untergrenze):
--     SR 14.9-15.8 -> 15.35, LR 14.9-16.2 -> 15.55.
UPDATE vehicle_specification SET official_consumption_kwh_per_100km = 15.35
WHERE car_model = 'EV_3' AND variant_name = 'EV3 58.3 kWh Standard Range (2024-)';
UPDATE vehicle_specification SET official_consumption_kwh_per_100km = 15.55
WHERE car_model = 'EV_3' AND variant_name = 'EV3 81.4 kWh Long Range (2024-)';

-- 2) Autos der manuellen Alt-/Fehl-Zeilen auf die kanonische Standard-Range-Zeile umhaengen
UPDATE car SET vehicle_specification_id = (
    SELECT id FROM vehicle_specification
    WHERE car_model = 'EV_3' AND variant_name = 'EV3 58.3 kWh Standard Range (2024-)'
    LIMIT 1
) WHERE vehicle_specification_id IN (
    SELECT id FROM vehicle_specification
    WHERE car_model = 'EV_3' AND variant_name IN ('', '61/58 kWh')
);

-- 3) Manuelle Alt-/Fehl-Zeilen entfernen
DELETE FROM vehicle_specification
WHERE car_model = 'EV_3' AND variant_name IN ('', '61/58 kWh');
