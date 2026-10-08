-- V198: Ford-Modellpalette nach WLTP vervollstaendigen.
--
-- Befund: Puma Gen-E, Capri, E-Tourneo Courier und E-Tourneo Custom fehlten komplett.
-- Mustang Mach-E hatte nur EPA-Zeilen plus eine WLTP-Zeile ohne Variante, Explorer nur MY24-25.
-- Bestehende Zeilen bleiben unveraendert (verknuepfte Autos), es wird nur ergaenzt.
--
-- Konvention wie V197: battery_capacity_kwh = brutto (Lookup-Key), net = nutzbar,
-- Verbrauch = WLTP TEL rated consumption (inkl. Ladeverluste), Reichweite = WLTP TEL.
-- Modelljahre mit identischen Werten sind zu einer Zeile zusammengefasst.
--
-- Quelle: ev-database.org, abgerufen am 08.10.2026 (car/<id>):
--   Mach-E MY21: 1242 SR RWD, 1244 SR AWD, 1243 ER RWD, 1245 ER AWD, 1246 GT
--   Mach-E MY22: 1668 SR RWD, 1670 SR AWD, 1669 ER RWD, 1671 ER AWD, 1672 GT
--   Mach-E MY23: 1753 SR RWD, 1755 SR AWD, 1754 ER RWD, 1756 ER AWD, 1757 GT
--   Mach-E MY23.75: 2034 SR RWD, 2036 SR AWD, 2035 ER RWD, 2037 ER AWD, 2038 GT
--   Mach-E MY24: 2277 SR RWD, 2276 SR AWD, 2278 ER RWD, 2279 ER AWD, 2280 GT
--   Mach-E MY25: 3187 SR RWD, 3188 ER RWD, 3189 ER AWD, 3190 GT
--   Explorer MY26/27: 3446/3644 SR RWD, 3454/3645 ER RWD, 3455/3646 ER AWD (MY27 = MY26)
--   Capri MY25: 2240 SR RWD, 2241 ER RWD, 2242 ER AWD; MY26/27: 3445/3647, 3452/3648, 3453/3649
--   Puma Gen-E: 3073 (MY24-25), 3597 (MY26)
--   e-Tourneo Courier: 3166
--   e-Tourneo Custom 2023-2025: 3162-3165 (L1/L2, 160/210 kW, alle identisch)
--   e-Tourneo Custom 2025+: 3420-3427 (L1/L2 x 160/210 kW x RWD/AWD)
--
-- Nicht enthalten, weil ev-database keine WLTP-Werte fuehrt: F-150 Lightning,
-- E-Transit, E-Transit Custom, E-Transit Courier (Nutzfahrzeuge).

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), 'FORD', v.model, v.brutto, v.netto, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.von, v.bis, NOW(), NOW()
FROM (VALUES
    -- Mustang Mach-E
    ('MUSTANG_MACH_E', 75.70, 68.0, 440, 17.2, 'Mach-E Standard Range RWD (MY21)',    DATE '2021-01-01', DATE '2022-04-30'),
    ('MUSTANG_MACH_E', 75.70, 68.0, 400, 19.5, 'Mach-E Standard Range AWD (MY21)',    DATE '2021-04-01', DATE '2022-04-30'),
    ('MUSTANG_MACH_E', 98.70, 88.0, 610, 16.5, 'Mach-E Extended Range RWD (MY21)',    DATE '2021-04-01', DATE '2022-04-30'),
    ('MUSTANG_MACH_E', 98.70, 88.0, 540, 18.7, 'Mach-E Extended Range AWD (MY21)',    DATE '2021-04-01', DATE '2022-04-30'),
    ('MUSTANG_MACH_E', 98.70, 88.0, 500, 20.0, 'Mach-E GT (MY21)',                    DATE '2021-07-01', DATE '2022-04-30'),
    ('MUSTANG_MACH_E', 75.70, 70.0, 440, 17.2, 'Mach-E Standard Range RWD (MY22-23)', DATE '2022-04-01', DATE '2023-11-30'),
    ('MUSTANG_MACH_E', 75.70, 70.0, 400, 19.5, 'Mach-E Standard Range AWD (MY22-23)', DATE '2022-04-01', DATE '2023-10-31'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 610, 16.5, 'Mach-E Extended Range RWD (MY22)',    DATE '2022-04-01', DATE '2022-11-30'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 540, 18.7, 'Mach-E Extended Range AWD (MY22)',    DATE '2022-04-01', DATE '2022-11-30'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 500, 20.0, 'Mach-E GT (MY22)',                    DATE '2022-04-01', DATE '2022-11-30'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 600, 17.3, 'Mach-E Extended Range RWD (MY23-24)', DATE '2022-11-01', DATE '2025-06-30'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 550, 18.8, 'Mach-E Extended Range AWD (MY23-24)', DATE '2022-11-01', DATE '2025-09-30'),
    ('MUSTANG_MACH_E', 98.70, 91.0, 490, 21.2, 'Mach-E GT (MY23)',                    DATE '2022-11-01', DATE '2024-08-31'),
    ('MUSTANG_MACH_E', 78.00, 72.6, 470, 17.8, 'Mach-E Standard Range RWD (MY23.75)', DATE '2023-10-01', DATE '2024-08-31'),
    ('MUSTANG_MACH_E', 78.00, 72.6, 428, 19.6, 'Mach-E Standard Range AWD (MY23.75)', DATE '2023-10-01', DATE '2024-08-31'),
    ('MUSTANG_MACH_E', 78.00, 72.6, 470, 17.9, 'Mach-E Standard Range RWD (MY24)',    DATE '2024-07-01', DATE '2025-06-30'),
    ('MUSTANG_MACH_E', 78.00, 72.6, 435, 19.1, 'Mach-E Standard Range AWD (MY24)',    DATE '2024-07-01', NULL),
    ('MUSTANG_MACH_E', 98.70, 91.0, 515, 21.0, 'Mach-E GT (MY24+)',                   DATE '2024-07-01', NULL),
    ('MUSTANG_MACH_E', 78.00, 72.6, 470, 18.5, 'Mach-E Standard Range RWD (MY25+)',   DATE '2025-04-01', NULL),
    ('MUSTANG_MACH_E', 98.70, 88.0, 615, 17.7, 'Mach-E Extended Range RWD (MY25+)',   DATE '2025-04-01', NULL),
    ('MUSTANG_MACH_E', 98.70, 88.0, 555, 19.3, 'Mach-E Extended Range AWD (MY25+)',   DATE '2025-04-01', NULL),
    -- Explorer ab MY26 (LFP-Standard-Range, ER RWD und AWD mit getauschten Akkus)
    ('EXPLORER_EV',    61.00, 58.0, 444, 15.4, 'Explorer Standard Range RWD (2026+)', DATE '2026-01-01', NULL),
    ('EXPLORER_EV',    84.00, 79.0, 602, 14.6, 'Explorer Extended Range RWD (2026+)', DATE '2026-01-01', NULL),
    ('EXPLORER_EV',    82.00, 77.0, 553, 16.3, 'Explorer Extended Range AWD (2026+)', DATE '2026-01-01', NULL),
    -- Capri
    ('CAPRI',          55.00, 52.0, 393, 15.5, 'Capri Standard Range RWD (2024-2025)', DATE '2024-07-01', DATE '2026-01-31'),
    ('CAPRI',          82.00, 77.0, 627, 13.3, 'Capri Extended Range RWD (2024-2025)', DATE '2024-07-01', DATE '2026-01-31'),
    ('CAPRI',          84.00, 79.0, 592, 15.0, 'Capri Extended Range AWD (2024-2025)', DATE '2024-07-01', DATE '2026-01-31'),
    ('CAPRI',          61.00, 58.0, 464, 14.8, 'Capri Standard Range RWD (2026+)',     DATE '2026-01-01', NULL),
    ('CAPRI',          84.00, 79.0, 627, 13.3, 'Capri Extended Range RWD (2026+)',     DATE '2026-01-01', NULL),
    ('CAPRI',          82.00, 77.0, 592, 15.0, 'Capri Extended Range AWD (2026+)',     DATE '2026-01-01', NULL),
    -- Puma Gen-E
    ('PUMA_GEN_E',     53.00, 43.6, 376, 13.1, 'Puma Gen-E (2024-2025)',              DATE '2024-12-01', DATE '2026-02-28'),
    ('PUMA_GEN_E',     53.00, 46.8, 417, 13.0, 'Puma Gen-E (2026+)',                  DATE '2026-03-01', NULL),
    -- e-Tourneo Courier
    ('E_TOURNEO_COURIER', 54.00, 43.6, 296, 16.9, 'e-Tourneo Courier (2024+)',        DATE '2024-12-01', NULL),
    -- e-Tourneo Custom
    ('E_TOURNEO_CUSTOM', 68.00, 64.0, 307, 24.2, 'e-Tourneo Custom 160/210 kW (2023-2025)', DATE '2023-11-01', DATE '2025-12-31'),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 339, 24.1, 'e-Tourneo Custom L1 160 kW RWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 339, 24.4, 'e-Tourneo Custom L2 160 kW RWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 328, 24.1, 'e-Tourneo Custom L1/L2 210 kW RWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 310, 26.5, 'e-Tourneo Custom L1 160 kW AWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 310, 26.8, 'e-Tourneo Custom L2 160 kW AWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 312, 26.4, 'e-Tourneo Custom L1 210 kW AWD (2025+)', DATE '2025-12-01', NULL),
    ('E_TOURNEO_CUSTOM', 75.00, 71.0, 312, 26.6, 'e-Tourneo Custom L2 210 kW AWD (2025+)', DATE '2025-12-01', NULL)
) AS v(model, brutto, netto, rw, verbrauch, variante, von, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification s
    WHERE s.car_brand = 'FORD' AND s.car_model = v.model AND s.variant_name = v.variante);
