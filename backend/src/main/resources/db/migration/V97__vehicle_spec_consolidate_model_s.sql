-- V97: Tesla Model S/X Bereinigung + Konsolidierung
-- 1. Duplikate entfernen (identische Specs ohne Datum, 0 cars)
-- 2. MODEL_S_PERFORMANCE + MODEL_S_PLAID unter MODEL_S zusammenfassen
-- 3. trim_level setzen für saubere Anzeige ohne Jahreszahlen im displayLabel

DELETE FROM vehicle_specification WHERE id = '709d050c-b0b7-4148-9c0e-f91e8ef2fc95';
DELETE FROM vehicle_specification WHERE id = 'a7c7dfa2-0831-4b0f-a233-a8317ae87796';
DELETE FROM vehicle_specification WHERE id = 'fd1812ac-8169-4af5-88a6-f3027565ebe5';

UPDATE vehicle_specification
SET car_model = 'MODEL_S'
WHERE car_model IN ('MODEL_S_PERFORMANCE', 'MODEL_S_PLAID');

UPDATE car SET model = 'MODEL_S' WHERE model = 'MODEL_S_PERFORMANCE';

UPDATE vehicle_specification SET trim_level = '75D'         WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND variant_name ILIKE '%75D%';
UPDATE vehicle_specification SET trim_level = '85 RWD'      WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND variant_name ILIKE '%Model S 85%';
UPDATE vehicle_specification SET trim_level = 'Dual Motor'  WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND variant_name ILIKE '%100D%';
UPDATE vehicle_specification SET trim_level = 'Performance' WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND variant_name ILIKE '%Performance%';
UPDATE vehicle_specification SET trim_level = 'Plaid'       WHERE car_brand = 'TESLA' AND car_model = 'MODEL_S' AND variant_name ILIKE '%Plaid%';
UPDATE vehicle_specification SET trim_level = 'Dual Motor'  WHERE car_brand = 'TESLA' AND car_model = 'MODEL_X';
