-- Kia EV9: WLTP-Varianten vervollstaendigen. Bisher existierte nur EINE WLTP-Zeile
-- (Long Range RWD). SR 76.1 und AWD lagen nur als EPA-Zeilen vor -> unsichtbar im
-- Auto-Formular-Selektor (CarController listet nur rating_source=WLTP + COMBINED).
-- Ein 76.1- oder AWD-Fahrer konnte seine Ausfuehrung nicht waehlen.
--
-- Quelle: Wikipedia (DE) / Kia. Verbrauch sind Einzel-WLTP-Werte je Variante (keine
-- Spanne) -> 1:1 uebernommen. EPA-Zeilen bleiben unangetastet (valide US-Daten fuer
-- die EPA-Vergleichssektion der oeffentlichen Modell-Seiten).
--
-- pickBestMatch-Fallback (Autos ohne Spec-Link) bei 99.8 kWh: nur die Basis
-- Long Range RWD bekommt ein available_from (2023-06-01) und gewinnt damit; AWD/GT
-- Line/GT bleiben NULL (verlieren, LocalDate.MIN). Exakte Auswahl laeuft ueber Trim +
-- Spec-Link. Lookup-Keys battery_capacity_kwh = CarModel-Enum cap()-Werte (76.1 / 99.8).

-- Basis Long Range RWD: sauberer variant_name + available_from setzen (Werte bereits korrekt).
UPDATE vehicle_specification
SET variant_name = 'EV9 99.8 kWh Long Range RWD (2023-)',
    available_from = '2023-06-01',
    updated_at = NOW()
WHERE car_model = 'EV_9' AND rating_source = 'WLTP' AND trim_level = 'Long Range RWD';

INSERT INTO vehicle_specification (
    id, car_brand, car_model,
    battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source,
    variant_name, trim_level,
    available_from, available_to,
    created_at, updated_at
) VALUES
-- 76.1 kWh Standard Range (RWD), Marktstart 12/2024. Einzige 76.1-WLTP-Zeile -> keine Kollision.
(gen_random_uuid(), 'KIA', 'EV_9', 76.10, 74.00, 443, 19.5, 'COMBINED', 'WLTP',
 'EV9 76.1 kWh Standard Range (2024-)', 'Standard Range', '2024-12-01', NULL, NOW(), NOW()),
-- 99.8 kWh AWD (385 PS).
(gen_random_uuid(), 'KIA', 'EV_9', 99.80, 96.00, 521, 21.7, 'COMBINED', 'WLTP',
 'EV9 99.8 kWh AWD (2023-)', 'AWD', NULL, NULL, NOW(), NOW()),
-- 99.8 kWh GT Line AWD (385 PS, hoeheres Drehmoment/Verbrauch als AWD).
(gen_random_uuid(), 'KIA', 'EV_9', 99.80, 96.00, 505, 22.8, 'COMBINED', 'WLTP',
 'EV9 99.8 kWh GT Line AWD (2023-)', 'GT Line AWD', NULL, NULL, NOW(), NOW()),
-- 99.8 kWh GT AWD (508 PS), Marktstart 05/2025.
(gen_random_uuid(), 'KIA', 'EV_9', 99.80, 96.00, 505, 22.8, 'COMBINED', 'WLTP',
 'EV9 99.8 kWh GT (2025-)', 'GT', NULL, NULL, NOW(), NOW());
