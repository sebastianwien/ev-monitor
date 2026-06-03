-- V128: VW ID.3 - alle 12 Varianten gemaess Wikipedia Technische Daten (Stand 06/2026)
-- Quelle: https://de.wikipedia.org/wiki/VW_ID.3 (Tabelle "Technische Daten")
--
-- Bestehende 5 Eintraege hatten Netto-Wert als Lookup-Key (battery_capacity_kwh = net).
-- Diese werden auf korrekte Brutto-Werte umgestellt + 7 fehlende Varianten ergaenzt.

-- ============================================================
-- Bestehende 5 Eintraege aktualisieren (WHERE = alter Prod-Wert)
-- ============================================================

-- Pure (2020-2024) → Pure Performance (01/2021-08/2021), 55 brutto / 45 netto
-- Prod hat bereits battery_capacity_kwh=55 (war schon korrigiert), WHERE matcht darauf
UPDATE vehicle_specification SET
    net_battery_capacity_kwh            = 45.00,
    official_range_km                   = 352,
    official_consumption_kwh_per_100km  = 15.0,
    variant_name                        = 'ID.3 Pure Performance (2021)',
    trim_level                          = 'Pure Performance',
    available_from                      = '2021-01-01',
    available_to                        = '2021-08-31',
    updated_at                          = NOW()
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 55.00
  AND variant_name = 'ID.3 Pure (2020-2024)';

-- Pro (2020-2024) → Pro 107 kW (11/2020-01/2022), 62 brutto / 58 netto
-- Prod hat bereits battery_capacity_kwh=62 (war schon korrigiert), WHERE matcht darauf
UPDATE vehicle_specification SET
    net_battery_capacity_kwh            = 58.00,
    official_range_km                   = 426,
    official_consumption_kwh_per_100km  = 15.4,
    variant_name                        = 'ID.3 Pro 107 kW (2020-2022)',
    trim_level                          = 'Pro',
    available_from                      = '2020-11-01',
    available_to                        = '2022-01-31',
    updated_at                          = NOW()
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 62.00
  AND variant_name = 'ID.3 Pro (2020-2024)';

-- Pro S (2021) → Pro Performance (11/2019-12/2022), 62 brutto / 58 netto
UPDATE vehicle_specification SET
    battery_capacity_kwh                = 62.00,
    net_battery_capacity_kwh            = 58.00,
    official_range_km                   = 426,
    official_consumption_kwh_per_100km  = 15.4,
    variant_name                        = 'ID.3 Pro Performance (2019-2022)',
    trim_level                          = 'Pro Performance',
    available_from                      = '2019-11-01',
    available_to                        = '2022-12-31',
    updated_at                          = NOW()
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 59.00;

-- Pro S (2021-2024) → Pro S (07/2020-12/2022), 82 brutto / 77 netto
UPDATE vehicle_specification SET
    battery_capacity_kwh                = 82.00,
    net_battery_capacity_kwh            = 77.00,
    official_range_km                   = 553,
    official_consumption_kwh_per_100km  = 15.8,
    variant_name                        = 'ID.3 Pro S (2020-2022)',
    trim_level                          = 'Pro S',
    available_from                      = '2020-07-01',
    available_to                        = '2022-12-31',
    updated_at                          = NOW()
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 77.00;

-- Pro S / GTX (2024-2026) → GTX (seit 05/2024), 86 brutto / 79 netto
UPDATE vehicle_specification SET
    battery_capacity_kwh                = 86.00,
    net_battery_capacity_kwh            = 79.00,
    official_range_km                   = 604,
    official_consumption_kwh_per_100km  = 14.5,
    variant_name                        = 'ID.3 GTX (2024-)',
    trim_level                          = 'GTX',
    available_from                      = '2024-05-01',
    available_to                        = NULL,
    updated_at                          = NOW()
WHERE car_brand = 'VW' AND car_model = 'ID_3' AND battery_capacity_kwh = 79.00;

-- ============================================================
-- 7 fehlende Varianten einfuegen
-- ============================================================

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
-- Pure Facelift (seit 05/2024), 55 brutto / 52 netto
(gen_random_uuid(), 'VW', 'ID_3', 55.00, 52.00, 388, 15.2, 'COMBINED', 'WLTP',
 'ID.3 Pure Facelift (2024-)', 'Pure', '2024-05-01', NULL, NOW(), NOW()),

-- Pro 150 kW (01/2023-05/2024), 62 brutto / 58 netto
(gen_random_uuid(), 'VW', 'ID_3', 62.00, 58.00, 429, 15.2, 'COMBINED', 'WLTP',
 'ID.3 Pro 150 kW (2023-2024)', 'Pro', '2023-01-01', '2024-05-31', NOW(), NOW()),

-- Pro Facelift (seit 05/2024), 63 brutto / 59 netto
(gen_random_uuid(), 'VW', 'ID_3', 63.00, 59.00, 434, 15.3, 'COMBINED', 'WLTP',
 'ID.3 Pro Facelift (2024-)', 'Pro', '2024-05-01', NULL, NOW(), NOW()),

-- Pro S (01/2023-05/2024), 82 brutto / 77 netto
(gen_random_uuid(), 'VW', 'ID_3', 82.00, 77.00, 559, 15.3, 'COMBINED', 'WLTP',
 'ID.3 Pro S (2023-2024)', 'Pro S', '2023-01-01', '2024-05-31', NOW(), NOW()),

-- Pro S Facelift (05/2024-05/2025), 82 brutto / 77 netto
(gen_random_uuid(), 'VW', 'ID_3', 82.00, 77.00, 557, 15.6, 'COMBINED', 'WLTP',
 'ID.3 Pro S Facelift (2024-2025)', 'Pro S', '2024-05-01', '2025-05-31', NOW(), NOW()),

-- Pro S (seit 05/2025), 86 brutto / 79 netto
(gen_random_uuid(), 'VW', 'ID_3', 86.00, 79.00, 568, 15.6, 'COMBINED', 'WLTP',
 'ID.3 Pro S (2025-)', 'Pro S', '2025-05-01', NULL, NOW(), NOW()),

-- GTX Performance (seit 08/2024), 86 brutto / 79 netto
(gen_random_uuid(), 'VW', 'ID_3', 86.00, 79.00, 601, 14.7, 'COMBINED', 'WLTP',
 'ID.3 GTX Performance (2024-)', 'GTX Performance', '2024-08-01', NULL, NOW(), NOW())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, variant_name, wltp_type, rating_source) DO NOTHING;

-- ============================================================
-- 3 Cars ohne vehicle_specification_id auf korrekte Spec verknuepfen
-- ============================================================

-- Car 669e0d04: Baujahr 2023, Trim "Pro 150 kW", 58 netto → ID.3 Pro 150 kW (2023-2024)
UPDATE car SET
    vehicle_specification_id = (
        SELECT id FROM vehicle_specification
        WHERE car_brand = 'VW' AND car_model = 'ID_3'
        AND variant_name = 'ID.3 Pro 150 kW (2023-2024)'
    ),
    custom_net_capacity_kwh = NULL
WHERE id = '669e0d04-178c-478d-afb2-c0409632bca0';

-- Car 446d2bab: Baujahr 2026, Trim "Pro Energy", 59 netto → ID.3 Pro Facelift (2024-)
UPDATE car SET
    vehicle_specification_id = (
        SELECT id FROM vehicle_specification
        WHERE car_brand = 'VW' AND car_model = 'ID_3'
        AND variant_name = 'ID.3 Pro Facelift (2024-)'
    ),
    custom_net_capacity_kwh = NULL
WHERE id = '446d2bab-e01b-4d5d-9821-55264d689c21';

-- Car a168215f: Baujahr 2024, Trim "Pro", 59 netto → ID.3 Pro Facelift (2024-)
UPDATE car SET
    vehicle_specification_id = (
        SELECT id FROM vehicle_specification
        WHERE car_brand = 'VW' AND car_model = 'ID_3'
        AND variant_name = 'ID.3 Pro Facelift (2024-)'
    ),
    custom_net_capacity_kwh = NULL
WHERE id = 'a168215f-b712-4959-a647-5acee2403802';
