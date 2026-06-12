-- V129: Opel - bestehende 3 Eintraege korrigieren + 6 fehlende Varianten ergaenzen
-- Verifiziert gegen offizielle WLTP-Angaben (Stand 06/2026):
--   Opel/Stellantis Media, ADAC Autokatalog, Carwow, mobile.de, EV Database
-- Konvention wie V128: battery_capacity_kwh = Brutto (Lookup-Key), net_battery_capacity_kwh = Netto

-- ============================================================
-- Bestehende 3 Eintraege korrigieren
-- ============================================================

-- Ampera-e: 423 km WLTP (nicht 413), Verbrauch 16.5 kWh/100km WLTP kombiniert (nicht 17.0)
-- Quelle: EV Database / goingelectric (WLTP-Homologation); NEFZ waren 520 km / 14.5
UPDATE vehicle_specification SET
    official_range_km                   = 423,
    official_consumption_kwh_per_100km  = 16.5,
    updated_at                          = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'AMPERA_E' AND battery_capacity_kwh = 60.00
  AND variant_name = 'Ampera-e (2017-2021)';

-- Corsa-e: Verbrauch 16.8 kWh/100km WLTP kombiniert (14.1 war netto/Reichweite gerechnet,
-- nicht der offizielle Wert), Reichweite 337 km (offizieller Wert zum 330er-Launch korrigiert)
-- Quelle: Opel/Carwow (337 km, 16.8 kWh/100km)
UPDATE vehicle_specification SET
    official_range_km                   = 337,
    official_consumption_kwh_per_100km  = 16.8,
    updated_at                          = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'CORSA_E' AND battery_capacity_kwh = 50.00
  AND variant_name = 'Corsa-e (2020-2023)';

-- Mokka-e: Verbrauch 16.2 kWh/100km WLTP kombiniert (offiziell 16.2-15.8; 17.5 war zu hoch)
-- Reichweite 324 km bleibt (offizieller Launch-Wert)
-- Quelle: Opel/Stellantis Media
UPDATE vehicle_specification SET
    official_consumption_kwh_per_100km  = 16.2,
    updated_at                          = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'MOKKA_E' AND battery_capacity_kwh = 50.00
  AND variant_name = 'Mokka-e (2021-2024)';

-- ============================================================
-- Fehlende Varianten ergaenzen
-- ============================================================

-- trim_level nur gesetzt, wo Opel selbst einen Varianten-Namen fuehrt (Long Range / Extended Range) -
-- reine Batteriegroessen-Varianten bleiben NULL (Frontend zeigt sie als Solo-Gruppen)
INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    created_at, updated_at
) VALUES
-- Corsa Electric Long Range (ab 10/2023, 115 kW): Marketing sagt "51 kWh", real 54 brutto / 51 netto
-- Quelle: ADAC ("Corsa Electric (54 kWh) GS"), Stellantis (405 km, 14.2 kWh/100km)
-- Hinweis: EV Database fuehrt abweichend 51 brutto / 48.1 netto - ADAC/Homologation hat Vorrang
(gen_random_uuid(), 'OPEL', 'CORSA_E', 54.00, 51.00, 405, 14.2, 'COMBINED', 'WLTP',
 'Corsa Electric Long Range (2023-)', 'Long Range', NOW(), NOW()),

-- Astra Electric (ab 2023, 115 kW): 54 brutto / 50.8 netto, 418 km, 14.8 kWh/100km
-- Quelle: Stellantis Media Press Kit (Sports Tourer: 413 km - bei Bedarf eigene Variante)
(gen_random_uuid(), 'OPEL', 'ASTRA_E', 54.00, 50.80, 418, 14.8, 'COMBINED', 'WLTP',
 'Astra Electric (2023-)', NULL, NOW(), NOW()),

-- Mokka Electric 54 kWh (ab 2023, 115 kW): 54 brutto / 50.8 netto, 406 km, 15.2 kWh/100km
-- Quelle: Stellantis Media
(gen_random_uuid(), 'OPEL', 'MOKKA_E', 54.00, 50.80, 406, 15.2, 'COMBINED', 'WLTP',
 'Mokka Electric 54 kWh (2023-)', NULL, NOW(), NOW()),

-- Combo-e Life (2021-2024): 50 brutto / 46.3 netto, 280 km, offiziell 19.3-21.5 kWh/100km
-- Quelle: Stellantis Media / firmenauto
(gen_random_uuid(), 'OPEL', 'COMBO_E', 50.00, 46.30, 280, 19.3, 'COMBINED', 'WLTP',
 'Combo-e Life (2021-2024)', NULL, NOW(), NOW()),

-- Vivaro-e 50 kWh (2020-2024): 50 brutto / 46.3 netto, 230 km, offiziell 24.1-27.7 kWh/100km
-- Quelle: Opel / EV Database
(gen_random_uuid(), 'OPEL', 'VIVARO_E', 50.00, 46.30, 230, 24.1, 'COMBINED', 'WLTP',
 'Vivaro-e 50 kWh (2020-2024)', NULL, NOW(), NOW()),

-- Vivaro-e 75 kWh (2020-2024): 75 brutto / 68 netto, 330 km, offiziell 21.7-24.4 kWh/100km
-- Quelle: Opel / EV Database
(gen_random_uuid(), 'OPEL', 'VIVARO_E', 75.00, 68.00, 330, 21.7, 'COMBINED', 'WLTP',
 'Vivaro-e 75 kWh (2020-2024)', NULL, NOW(), NOW()),

-- Astra Electric Facelift (ab 2026): 58.3 brutto / 55.4 netto (Opel-Angabe; EV Database rundet 58.4),
-- 454 km, 14.8 kWh/100km (EV Database Rated/TEL inkl. Ladeverluste)
-- Quelle: InsideEVs (Opel-Angaben), EV Database MY26
(gen_random_uuid(), 'OPEL', 'ASTRA_E', 58.30, 55.40, 454, 14.8, 'COMBINED', 'WLTP',
 'Astra Electric Facelift (2026-)', NULL, NOW(), NOW()),

-- Grandland Electric (ab 2024, 157 kW, STLA Medium): 77 brutto / 73 netto (Marketing "73 kWh"),
-- 521 km / 17.8 kWh/100km (offizielles WLTP3-Paar aus Stellantis-Fussnote; Launch-Kommunikation war 523 km)
-- Hinweis: AWD-Variante (17.9 kWh/100km) existiert, hier nicht separat gepflegt
(gen_random_uuid(), 'OPEL', 'GRANDLAND', 77.00, 73.00, 521, 17.8, 'COMBINED', 'WLTP',
 'Grandland Electric (2024-)', NULL, NOW(), NOW()),

-- Grandland Electric Long Range (ab 10/2025, 170 kW): 98 brutto / 96.9 netto (Marketing "97 kWh"),
-- 694 km / 18.6 kWh/100km (offizielles Paar aus Stellantis-Pressemitteilung, WLTP1/WLTP3-Fussnote)
(gen_random_uuid(), 'OPEL', 'GRANDLAND', 98.00, 96.90, 694, 18.6, 'COMBINED', 'WLTP',
 'Grandland Electric Long Range (2025-)', 'Long Range', NOW(), NOW()),

-- Frontera Electric (ab 2024, 83 kW, LFP): 44 brutto / 43.8 netto, 305 km,
-- offiziell 18.2-18.5 kWh/100km (WLTP2)
-- Quelle: Stellantis Media / EV Database
(gen_random_uuid(), 'OPEL', 'FRONTERA', 44.00, 43.80, 305, 18.2, 'COMBINED', 'WLTP',
 'Frontera Electric (2024-)', NULL, NOW(), NOW()),

-- Frontera Electric Extended Range (ab 07/2025, 83 kW, LFP): 54 brutto / 53.5 netto, 408 km,
-- offiziell 15.8-16.5 kWh/100km (WLTP3)
-- Quelle: Stellantis Media / electrive / EV Database
(gen_random_uuid(), 'OPEL', 'FRONTERA', 54.00, 53.50, 408, 15.8, 'COMBINED', 'WLTP',
 'Frontera Electric Extended Range (2025-)', 'Extended Range', NOW(), NOW());
