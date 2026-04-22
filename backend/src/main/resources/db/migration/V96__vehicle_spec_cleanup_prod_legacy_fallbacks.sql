-- V95: Bereinigt prod-spezifische Fallback-Specs mit veralteten Variant-Namen
-- Diese NULL-dated Specs existieren nur auf Prod weil V94 andere variant_name-Werte erwartete.
-- Idempotent: DELETEs sind no-ops wenn die Rows bereits entfernt wurden.

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 LR AWD (2021-2023)' AND available_from IS NULL;

DELETE FROM vehicle_specification
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name = 'Model 3 SR+/RWD LFP (2020-2023)' AND available_from IS NULL;
