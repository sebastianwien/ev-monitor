-- V93: trim_level als strukturierter Gruppierungsschlüssel für Varianten-Anzeige auf Public Pages
-- Ermöglicht Gruppierung mehrerer Spec-Rows (z.B. alle Tesla LR AWD-Varianten über Baujahre)
-- zu einer Anzeige-Zeile mit WLTP-Wertebereich statt vieler Einzelzeilen.
-- Ansatz: Pattern-Matching auf bestehende variant_name Werte - kein Umbenennen, kein INSERT.
-- Phase 1: Tesla Model 3/Y/S/X - andere Marken bei Bedarf später.

ALTER TABLE vehicle_specification
    ADD COLUMN trim_level VARCHAR(30);

-- ============================================================
-- Tesla MODEL_3
-- ============================================================

-- Standard Range: Standard Plus, SR+, standalone RWD (ohne LR/Premium)
UPDATE vehicle_specification SET trim_level = 'Standard Range'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND (variant_name ILIKE '%Standard%'
    OR variant_name ILIKE '%SR+%'
    OR variant_name ILIKE '%RWD LFP%'
    OR variant_name ILIKE 'Model 3 RWD %');

-- Long Range RWD: LR RWD oder Premium RWD (Highland)
UPDATE vehicle_specification SET trim_level = 'Long Range RWD'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND (variant_name ILIKE '%LR RWD%'
    OR variant_name ILIKE '%Premium RWD%');

-- Long Range AWD: LR AWD oder Premium AWD (Highland)
UPDATE vehicle_specification SET trim_level = 'Long Range AWD'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND (variant_name ILIKE '%LR AWD%'
    OR variant_name ILIKE '%Premium AWD%');

-- Performance: alle Performance-Varianten inkl. gemischte "Performance / LR" Einträge
UPDATE vehicle_specification SET trim_level = 'Performance'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_3'
  AND variant_name ILIKE '%Performance%';

-- ============================================================
-- Tesla MODEL_Y
-- ============================================================

-- Standard Range: RWD Varianten (LFP, Juniper)
UPDATE vehicle_specification SET trim_level = 'Standard Range'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND (variant_name ILIKE '%RWD LFP%'
    OR variant_name ILIKE '%RWD Juniper%'
    OR variant_name = 'Model Y RWD');

-- Long Range RWD
UPDATE vehicle_specification SET trim_level = 'Long Range RWD'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name ILIKE '%LR RWD%';

-- Long Range AWD: LR AWD (alle Varianten ohne "Performance")
UPDATE vehicle_specification SET trim_level = 'Long Range AWD'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name ILIKE '%LR AWD%'
  AND variant_name NOT ILIKE '%Performance%';

-- Performance: Performance-Varianten inkl. gemischte "Performance / LR AWD" Einträge
UPDATE vehicle_specification SET trim_level = 'Performance'
WHERE car_brand = 'TESLA' AND car_model = 'MODEL_Y'
  AND variant_name ILIKE '%Performance%';

