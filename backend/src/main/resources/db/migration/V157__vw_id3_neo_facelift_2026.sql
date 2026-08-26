-- V157: VW ID.3 Neo (2. Modellpflege, Marktstart 04/2026) - 3 Batterievarianten
--
-- Quellen (gegengeprueft):
--   https://de.wikipedia.org/wiki/VW_ID.3 (Tabelle "Technische Daten", Abschnitt "ID.3 Neo ab 2026")
--   https://ev-database.org/car/3520 | /3521 | /3522 (MY27), jeweils TEL-Werte
--
-- Abweichung: EVDB listet fuer die 50-kWh-Variante 61,0 kWh brutto - denselben Wert wie
-- fuer die 58-kWh-Variante. Wikipedia nennt einen eigenen 52-kWh-LFP-Pack. Uebernommen wird
-- Wikipedia (52), da der EVDB-Wert nach Uebertragungsfehler aus der 58er-Zeile aussieht.
--
-- Verbrauch/Reichweite folgen der Konvention der Bestandszeilen (EVDB TEL, "rated"):
-- gegengeprueft an "ID.3 Pro S (2025-)" = 568 km / 15,60 == EVDB TEL 353 mi / 251 Wh/mi.
--
-- trim_level ist gesetzt, weil ID_3 im Auto-Formular nach trim gruppiert wird
-- (useCarForm.ts: Capacities ohne trimLevel werden uebersprungen).
-- VW selbst fuehrt beim Neo nur noch die Ausstattungslinien Trend/Life/Style, die n:m
-- auf die Batterien abbilden (Trend: 50; Life: 50/58/79; Style: 58/79) und deshalb als
-- Spec-Schluessel ungeeignet sind. Beibehalten wird daher die Pure/Pro/Pro S-Systematik
-- der Bestandszeilen.

-- ============================================================
-- Vorgaenger abschliessen (EVDB fuehrt Pure/Pro/GTX als "MY24-26",
-- Pro S als "MY26" - alle bis 04/2026)
-- ============================================================
UPDATE vehicle_specification SET
    available_to = '2026-03-31',
    updated_at   = NOW()
WHERE car_brand    = 'VW'
  AND car_model    = 'ID_3'
  AND available_to IS NULL
  AND available_from IS NOT NULL;

-- ============================================================
-- Neue Varianten
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
-- 125 kW (170 PS), LFP, 100 kW DC
(gen_random_uuid(), 'VW', 'ID_3', 52.00, 50.00, 417, 14.0, 'COMBINED', 'WLTP',
 'ID.3 Neo Pure (2026-)', 'Neo Pure', '2026-04-01', NULL, NOW(), NOW()),
-- 140 kW (190 PS), 105 kW DC
(gen_random_uuid(), 'VW', 'ID_3', 61.00, 58.00, 494, 13.9, 'COMBINED', 'WLTP',
 'ID.3 Neo Pro (2026-)', 'Neo Pro', '2026-04-01', NULL, NOW(), NOW()),
-- 170 kW (231 PS), NMC, 183 kW DC
(gen_random_uuid(), 'VW', 'ID_3', 84.00, 79.00, 630, 14.4, 'COMBINED', 'WLTP',
 'ID.3 Neo Pro S (2026-)', 'Neo Pro S', '2026-04-01', NULL, NOW(), NOW());
