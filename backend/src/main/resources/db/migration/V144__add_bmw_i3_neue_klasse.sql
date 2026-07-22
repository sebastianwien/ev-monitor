-- BMW i3 Neue Klasse (Sedan, Code NA0) als eigenes Modell. Voellig anderes Auto als der
-- alte i3 (City-Car, 33/42 kWh) -> getrennter Enum-Eintrag I3_NEUE_KLASSE, damit Selektor
-- und Verbrauchs-Lookup nicht vermischt werden.
--
-- Nur das Launch-Modell i3 50 xDrive (AWD, 345 kW) ist offiziell spezifiziert; der i3 40
-- (RWD, 82.6 kWh nutzbar) kommt spaeter und hat noch keine offiziellen EnVKV-Verbrauchswerte
-- -> bewusst weggelassen (nicht raten).
--
-- Quelle: BMW Press DE / EnVKV (i3 50 xDrive First Edition). Verbrauch WLTP kombiniert
-- 13.5-16.1 kWh/100km -> Mittelwert 14.8 (Projektregel: Verbrauch immer Mittelwert).
-- Reichweite WLTP 758-906 km -> Headline-Obergrenze 906 km (analog uebrige Katalog-Eintraege).
-- Nutzbar 108.7 kWh offiziell bestaetigt; brutto 113.4 kWh (iX3-Pack) = Lookup-Key.
-- Einzige Kapazitaet -> keine pickBestMatch-Kollision.
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
(gen_random_uuid(), 'BMW', 'I3_NEUE_KLASSE', 113.40, 108.70, 906, 14.8, 'COMBINED', 'WLTP',
 'i3 50 xDrive 113.4 kWh (2026-)', '50 xDrive', '2026-10-01', NULL, NOW(), NOW());
