-- Add variant_name to vehicle_specification for human-readable battery variant labels
-- e.g. "Long Range", "Performance", "Pro S" — null for models without clear naming
ALTER TABLE vehicle_specification ADD COLUMN variant_name VARCHAR(100);

-- Tesla Model 3 (enum: 57.5, 75.0, 79.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range+'  WHERE car_model = 'MODEL_3' AND battery_capacity_kwh = 57.50;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'MODEL_3' AND battery_capacity_kwh = 75.00;
UPDATE vehicle_specification SET variant_name = 'Performance'       WHERE car_model = 'MODEL_3' AND battery_capacity_kwh = 79.00;

-- Tesla Model Y (enum: 60.0, 75.0, 79.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'MODEL_Y' AND battery_capacity_kwh = 60.00;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'MODEL_Y' AND battery_capacity_kwh = 75.00;
UPDATE vehicle_specification SET variant_name = 'Performance'       WHERE car_model = 'MODEL_Y' AND battery_capacity_kwh = 79.00;

-- VW ID.3 (enum: 45.0, 58.0, 77.0, 79.0)
UPDATE vehicle_specification SET variant_name = 'Pure'              WHERE car_model = 'ID_3' AND battery_capacity_kwh = 45.00;
UPDATE vehicle_specification SET variant_name = 'Pro'               WHERE car_model = 'ID_3' AND battery_capacity_kwh = 58.00;
UPDATE vehicle_specification SET variant_name = 'Pro S'             WHERE car_model = 'ID_3' AND battery_capacity_kwh = 77.00;
UPDATE vehicle_specification SET variant_name = 'Tour'              WHERE car_model = 'ID_3' AND battery_capacity_kwh = 79.00;

-- VW ID.4 (enum: 52.0, 77.0, 82.0)
UPDATE vehicle_specification SET variant_name = 'Pure'              WHERE car_model = 'ID_4' AND battery_capacity_kwh = 52.00;
UPDATE vehicle_specification SET variant_name = 'Pro'               WHERE car_model = 'ID_4' AND battery_capacity_kwh = 77.00;
UPDATE vehicle_specification SET variant_name = 'Pro S'             WHERE car_model = 'ID_4' AND battery_capacity_kwh = 82.00;

-- VW ID.5 (enum: 77.0 only)
UPDATE vehicle_specification SET variant_name = 'Pro'               WHERE car_model = 'ID_5' AND battery_capacity_kwh = 77.00;

-- Hyundai Ioniq 5 (enum: 58.0, 77.4, 84.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'IONIQ_5' AND battery_capacity_kwh = 58.00;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'IONIQ_5' AND battery_capacity_kwh = 77.40;
UPDATE vehicle_specification SET variant_name = 'Extended Range'    WHERE car_model = 'IONIQ_5' AND battery_capacity_kwh = 84.00;

-- Hyundai Ioniq 6 (enum: 53.0, 63.0, 77.4, 84.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'IONIQ_6' AND battery_capacity_kwh = 53.00;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'IONIQ_6' AND battery_capacity_kwh = 77.40;

-- Kia EV6 (enum: 58.0, 77.4, 84.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'EV_6' AND battery_capacity_kwh = 58.00;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'EV_6' AND battery_capacity_kwh = 77.40;
UPDATE vehicle_specification SET variant_name = 'Long Range Extended' WHERE car_model = 'EV_6' AND battery_capacity_kwh = 84.00;

-- Kia EV9 (enum: 76.1, 99.8)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'EV_9' AND battery_capacity_kwh = 76.10;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'EV_9' AND battery_capacity_kwh = 99.80;

-- BMW i4 (enum: 67.0, 80.7)
-- 67.0 = eDrive35 (kleinere Batterie), 80.7 = eDrive40 (eDrive40 und M50 teilen dieselbe Batterie)
UPDATE vehicle_specification SET variant_name = 'eDrive35'          WHERE car_model = 'I4' AND battery_capacity_kwh = 67.00;
UPDATE vehicle_specification SET variant_name = 'eDrive40 / M50'    WHERE car_model = 'I4' AND battery_capacity_kwh = 80.70;

-- Polestar 2 (enum: 67.0, 75.0, 78.0, 82.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'POLESTAR_2' AND battery_capacity_kwh = 67.00;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'POLESTAR_2' AND battery_capacity_kwh = 78.00;
UPDATE vehicle_specification SET variant_name = 'Long Range (2024)'  WHERE car_model = 'POLESTAR_2' AND battery_capacity_kwh = 82.00;

-- Skoda Enyaq (enum: 52.0, 55.0, 62.0, 77.0, 82.0)
-- Nummerierung entspricht ca. Nettowert: iV 50 ≈ 55 kWh brutto, iV 60 ≈ 62, iV 80 ≈ 82
UPDATE vehicle_specification SET variant_name = 'iV 50'             WHERE car_model = 'ENYAQ' AND battery_capacity_kwh = 55.00;
UPDATE vehicle_specification SET variant_name = 'iV 60'             WHERE car_model = 'ENYAQ' AND battery_capacity_kwh = 62.00;
UPDATE vehicle_specification SET variant_name = 'iV 80'             WHERE car_model = 'ENYAQ' AND battery_capacity_kwh = 82.00;

-- Audi Q4 e-tron (enum: 52.0, 77.0, 82.0)
-- Nur 35 e-tron (52 kWh) ist eindeutig zuordenbar; 82 kWh teilen sich 40 und 50 quattro
UPDATE vehicle_specification SET variant_name = '35 e-tron'         WHERE car_model = 'Q4_E_TRON' AND battery_capacity_kwh = 52.00;

-- Ford Mustang Mach-E (enum: 68.0, 75.7, 88.0, 91.0)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'MUSTANG_MACH_E' AND battery_capacity_kwh = 68.00;
UPDATE vehicle_specification SET variant_name = 'Extended Range'    WHERE car_model = 'MUSTANG_MACH_E' AND battery_capacity_kwh = 88.00;

-- Nissan Leaf (enum: 40.0, 59.0, 62.0)
UPDATE vehicle_specification SET variant_name = '40 kWh'            WHERE car_model = 'LEAF' AND battery_capacity_kwh = 40.00;
UPDATE vehicle_specification SET variant_name = 'e+ 62 kWh'         WHERE car_model = 'LEAF' AND battery_capacity_kwh = 62.00;

-- Renault Zoe (enum: 41.0, 52.0, 55.0)
UPDATE vehicle_specification SET variant_name = 'Z.E. 40'           WHERE car_model = 'ZOE' AND battery_capacity_kwh = 41.00;
UPDATE vehicle_specification SET variant_name = 'Z.E. 50'           WHERE car_model = 'ZOE' AND battery_capacity_kwh = 52.00;

-- BYD Seal (enum: 61.4, 82.5)
UPDATE vehicle_specification SET variant_name = 'Standard Range'    WHERE car_model = 'BYD_SEAL' AND battery_capacity_kwh = 61.40;
UPDATE vehicle_specification SET variant_name = 'Long Range'        WHERE car_model = 'BYD_SEAL' AND battery_capacity_kwh = 82.50;
