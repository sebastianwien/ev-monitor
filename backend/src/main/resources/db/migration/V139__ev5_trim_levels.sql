-- EV5-Auswahl im FE zeigte technische variant_name-Labels ("EV5 81.4 kWh 2WD (2025-) - 81.4 kWh"),
-- weil trim_level fehlte: useCarForm gruppiert nur nach Trim, wenn trim_level gesetzt ist, sonst
-- greift die flache Liste mit variant_name. EV3 war korrekt, weil dort trim_level gesetzt war.
-- EV5 EU hat nur einen Akku (81.4 kWh) -> Unterscheider ist der Antrieb.
UPDATE vehicle_specification SET trim_level = '2WD'
WHERE car_brand = 'KIA' AND car_model = 'EV_5' AND variant_name = 'EV5 81.4 kWh 2WD (2025-)';

UPDATE vehicle_specification SET trim_level = 'AWD'
WHERE car_brand = 'KIA' AND car_model = 'EV_5' AND variant_name = 'EV5 81.4 kWh AWD (2025-)';
