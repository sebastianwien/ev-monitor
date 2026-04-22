-- V94: Bereinigung überlappender Tesla Model 3 Varianten
-- Entfernt Duplikate, Fallback-Einträge ohne Datum und überlappende Subvarianten ohne Car-Referenz.
-- Führt Oct/Dec 2025 Übergangsvarianten zu einem Eintrag zusammen.

-- ============================================================
-- Standard Range: überlappende Subvarianten + Fallback entfernen
-- ============================================================
DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name IN (
    'Model 3 Standard Plus CATL LFP55',
    'Model 3 Standard Plus PANA 2170L',
    'Model 3 RWD CATL LFP60'
  );

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 SR+/RWD LFP' AND available_from IS NULL;

-- Standard Range: RWD Highland CATL LFP64 in RWD Highland zusammenführen
UPDATE vehicle_specification
SET available_from = '2025-05-01'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 RWD Highland' AND available_from = '2025-12-01';

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 RWD Highland CATL LFP64';

-- ============================================================
-- Long Range AWD: Fallback + Doppelter Highland-Übergang
-- ============================================================
DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 LR AWD' AND available_from IS NULL;

UPDATE vehicle_specification SET available_from = '2025-10-01'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 Premium AWD Highland' AND available_from = '2025-12-01';

-- Cars auf Premium AWD Highland umhängen bevor die alte Spec gelöscht wird
UPDATE car SET vehicle_specification_id = (
    SELECT id FROM vehicle_specification
    WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
      AND variant_name = 'Model 3 Premium AWD Highland'
    LIMIT 1
)
WHERE vehicle_specification_id = (
    SELECT id FROM vehicle_specification
    WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
      AND variant_name = 'Model 3 LR AWD Highland' AND available_from = '2025-10-01'
    LIMIT 1
);

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 LR AWD Highland' AND available_from = '2025-10-01';

-- ============================================================
-- Long Range RWD: Doppelter Highland-Übergang
-- ============================================================
UPDATE vehicle_specification SET available_from = '2025-10-01'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 Premium RWD Highland' AND available_from = '2025-12-01';

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 LR RWD Highland' AND available_from = '2025-10-01';

-- ============================================================
-- Performance: Overlap + Fallback entfernen
-- ============================================================
DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 Performance Highland' AND available_from = '2024-04-01';

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 Performance / LR Highland' AND available_from IS NULL;
