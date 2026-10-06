-- V197: VW-Drillinge (e-up!, Citigo e iV, Mii electric) vervollstaendigen.
--
-- Befund: Nur VW E_UP war angelegt, Citigo und Mii fehlten, SEAT hatte keine Zeile.
-- Alle drei teilen Antrieb und Akku: 36,8 kWh brutto / 32,3 kWh netto, 61 kW.
--
-- Konvention wie V154/V184/V186: battery_capacity_kwh = brutto (Lookup-Key), net = nutzbar,
-- Verbrauch = WLTP rated consumption, Reichweite = WLTP combined.
--
-- Quelle: ev-database.org, abgerufen am 06.10.2026 (WLTP TEL, rated consumption):
--   VW e-up! car/1189 (2020-2021: 258 km, 14.4) und car/1650 (2021-2023: 260 km, 14.4)
--   Skoda Citigo e iV car/1190 (257 km, 14.5), Seat Mii electric car/1191 (259 km, 14.4)
--
-- e-up!: V87 kannte eine Brutto-Zeile (36.80) und eine Netto-Zeile (32.30). Wo nur die
-- Netto-Zeile existiert, wird sie auf den Brutto-Key gehoben (id bleibt, verknuepfte Autos
-- bleiben haengen). Wo beide existieren (Prod-Drift), bleibt alles unveraendert.

UPDATE vehicle_specification
SET battery_capacity_kwh = 36.80, net_battery_capacity_kwh = 32.3,
    official_range_km = 260, official_consumption_kwh_per_100km = 14.4,
    variant_name = 'e-up! (2020-2023)',
    available_from = '2020-01-01', available_to = '2023-12-31', updated_at = NOW()
WHERE car_brand = 'VW' AND car_model = 'E_UP' AND battery_capacity_kwh = 32.30
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_specification
      WHERE car_brand = 'VW' AND car_model = 'E_UP' AND battery_capacity_kwh = 36.80);

INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km, wltp_type, rating_source,
    variant_name, available_from, available_to, created_at, updated_at)
SELECT gen_random_uuid(), v.brand, v.model, 36.80, 32.3, v.rw, v.verbrauch, 'COMBINED', 'WLTP',
       v.variante, v.von, v.bis, NOW(), NOW()
FROM (VALUES
    ('SKODA', 'CITIGO_E_IV',  257, 14.5, 'Citigo e iV (2020-2021)',  DATE '2020-01-01', DATE '2021-12-31'),
    ('SEAT',  'MII_ELECTRIC', 259, 14.4, 'Mii electric (2020-2021)', DATE '2020-01-01', DATE '2021-12-31')
) AS v(brand, model, rw, verbrauch, variante, von, bis)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_specification s
    WHERE s.car_brand = v.brand AND s.car_model = v.model);
