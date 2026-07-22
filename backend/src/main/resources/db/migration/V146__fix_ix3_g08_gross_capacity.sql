-- Alter BMW iX3 (G08, 2021-2024): battery_capacity_kwh (Brutto/Lookup-Key) war 74.0 und damit
-- gleich dem Netto-Wert - untertrieben. Der G08 hat real 80 kWh brutto / 74 kWh nutzbar.
-- Korrigiert auf 80.0 (analog Brutto-Konvention EV9/i3/iX3-NK); net_battery_capacity_kwh
-- bleibt 74.0.
--
-- Safe: auf Prod existiert genau 1 iX3, und der ist spec-verknuepft (nutzt findById, nicht
-- den by-Kapazitaet-Fallback) -> Verbrauch/Netto unveraendert. 0 nicht-verknuepfte iX3-Autos,
-- also kein Fallback-Match-Bruch. Enum cap() wird parallel auf 80.0 angehoben (Invariante
-- cap == battery_capacity_kwh). Zusaetzlich sauberer variant_name statt Leerstring.
UPDATE vehicle_specification
SET battery_capacity_kwh = 80.00,
    variant_name = 'iX3 80 kWh (2021-2024)',
    updated_at = NOW()
WHERE car_model = 'IX3' AND rating_source = 'WLTP' AND battery_capacity_kwh = 74.00;
