-- Kia EV5 (EU-Markt, seit 09/2025): fehlte bisher komplett (nur im CarModel-Enum, keine Spec-Zeile).
-- EU-Version hat ausschliesslich den 81.4 kWh NMC-Akku (78.0 kWh netto), zwei Antriebe.
-- Die kleinere 64.2 kWh LFP-Batterie gibt es nur in China/anderen Maerkten, nicht in Europa.
-- Quellen: EV Database (2WD: 530 km / 16.9 kWh/100km; AWD: 491 km / 18.1 kWh/100km), ADAC, Kia DE.
--
-- Anleitung zum Anlegen neuer Fahrzeug-Specs: docs/development/vehicle-specifications.md
--
-- Lookup-Key ist battery_capacity_kwh = 81.40 (== CarModel-Enum cap(81.4)); beide Zeilen teilen
-- ihn und werden ueber variant_name (NOT NULL, Teil des Unique-Constraints) unterschieden.
-- available_from macht pickBestMatch deterministisch: Der by-Kapazitaet-Fallback (fuer Cars ohne
-- verknuepfte vehicle_specification_id) waehlt die spaeter verfuegbare AWD-Zeile und damit die
-- lenientere 18.1-Referenz - das vermeidet faelschliche Plausibilitaets-Ablehnungen. Cars MIT
-- gesetzter vehicle_specification_id matchen ohnehin exakt und ignorieren diese Ordnung.
-- AWD-Datum (11/2025) approximiert die spaetere Einfuehrung (ADAC listet GT-Line "ab 11/25").
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'KIA', 'EV_5', 81.40, 78.00, 530, 16.9, 'COMBINED', 'WLTP',
 'EV5 81.4 kWh 2WD (2025-)', NULL, '2025-09-01', NULL, NOW(), NOW()),
(gen_random_uuid(), 'KIA', 'EV_5', 81.40, 78.00, 491, 18.1, 'COMBINED', 'WLTP',
 'EV5 81.4 kWh AWD (2025-)', NULL, '2025-11-01', NULL, NOW(), NOW());
