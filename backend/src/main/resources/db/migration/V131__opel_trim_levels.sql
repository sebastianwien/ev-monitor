-- V131: Opel - trim_level fuer alle Varianten setzen (PS + Marketing-kWh als Discriminator)
-- Schema: "<PS> PS · <kWh> kWh", offizielle Opel-Namen (Long Range / Extended Range) vorangestellt.
-- kWh = Marketing-Wert wie von Opel kommuniziert (teils netto: Corsa 51, Grandland 73/97).
--
-- WICHTIG (Frontend-Regel): pro Modell muessen ALLE Spec-Zeilen einen trim_level haben oder keine -
-- groupCapacitiesByTrim() im Frontend verwirft Optionen ohne Trim, sobald eine Option einen hat.

-- "Extended Range · 113 PS · 54 kWh" hat 32 Zeichen - varchar(30) reicht nicht mehr
ALTER TABLE vehicle_specification ALTER COLUMN trim_level TYPE VARCHAR(40);

UPDATE vehicle_specification SET trim_level = '204 PS · 60 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'AMPERA_E' AND battery_capacity_kwh = 60.00;

UPDATE vehicle_specification SET trim_level = '136 PS · 50 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'CORSA_E' AND battery_capacity_kwh = 50.00;

UPDATE vehicle_specification SET trim_level = 'Long Range · 156 PS · 51 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'CORSA_E' AND battery_capacity_kwh = 54.00;

UPDATE vehicle_specification SET trim_level = '156 PS · 54 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'ASTRA_E' AND battery_capacity_kwh = 54.00;

UPDATE vehicle_specification SET trim_level = '156 PS · 58 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'ASTRA_E' AND battery_capacity_kwh = 58.30;

UPDATE vehicle_specification SET trim_level = '136 PS · 50 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'MOKKA_E' AND battery_capacity_kwh = 50.00;

UPDATE vehicle_specification SET trim_level = '156 PS · 54 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'MOKKA_E' AND battery_capacity_kwh = 54.00;

UPDATE vehicle_specification SET trim_level = '136 PS · 50 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'COMBO_E' AND battery_capacity_kwh = 50.00;

UPDATE vehicle_specification SET trim_level = '136 PS · 50 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'VIVARO_E' AND battery_capacity_kwh = 50.00;

UPDATE vehicle_specification SET trim_level = '136 PS · 75 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'VIVARO_E' AND battery_capacity_kwh = 75.00;

UPDATE vehicle_specification SET trim_level = '213 PS · 73 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'GRANDLAND' AND battery_capacity_kwh = 77.00;

UPDATE vehicle_specification SET trim_level = 'Long Range · 231 PS · 97 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'GRANDLAND' AND battery_capacity_kwh = 98.00;

UPDATE vehicle_specification SET trim_level = '113 PS · 44 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'FRONTERA' AND battery_capacity_kwh = 44.00;

UPDATE vehicle_specification SET trim_level = 'Extended Range · 113 PS · 54 kWh', updated_at = NOW()
WHERE car_brand = 'OPEL' AND car_model = 'FRONTERA' AND battery_capacity_kwh = 54.00;
