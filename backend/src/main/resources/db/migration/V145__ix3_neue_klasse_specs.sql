-- BMW iX3 Neue Klasse (Code NA5): Specs ergaenzen. Der Enum-Eintrag IX3_NEUE_KLASSE
-- existierte bisher nur als Platzhalter ohne vehicle_specification-Zeilen (leerer
-- Katalog-Eintrag, Enum-Fallback). Zwei offizielle Antriebsvarianten:
--   - iX3 50 xDrive (AWD, 345 kW): brutto 113.4 / netto 108.7 kWh (gleicher Pack wie i3 NK)
--   - iX3 40 (RWD, 235 kW): brutto 87.5 / netto 82.6 kWh (Einstiegsmodell, ab 07/2026)
--
-- Quelle: BMW Press DE / EnVKV. Verbrauch = WLTP kombiniert:
--   50 xDrive 18.1-15.1 kWh/100km -> Mittelwert 16.6 (Projektregel: Verbrauch immer Mittelwert)
--   40        14.6 kWh/100km (offizieller Einzelwert, vorlaeufig) -> 1:1
-- Reichweite = WLTP-Obergrenze/Headline: 50 xDrive 805 km (673-805), 40 bis 635 km.
-- Lookup-Keys battery_capacity_kwh = CarModel-Enum cap()-Werte (87.5 / 113.4).
-- Unterschiedliche Kapazitaeten -> keine pickBestMatch-Kollision, available_from bleibt NULL.
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'BMW', 'IX3_NEUE_KLASSE', 87.50, 82.60, 635, 14.6, 'COMBINED', 'WLTP',
 'iX3 40 87.5 kWh (2026-)', '40', NULL, NULL, NOW(), NOW()),
(gen_random_uuid(), 'BMW', 'IX3_NEUE_KLASSE', 113.40, 108.70, 805, 16.6, 'COMBINED', 'WLTP',
 'iX3 50 xDrive 113.4 kWh (2025-)', '50 xDrive', NULL, NULL, NOW(), NOW());
