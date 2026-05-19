-- Cleanup nach V120 + customNet-Invariant-Tightening:
--
-- Neue Regel: car.custom_net_capacity_kwh ist nur gesetzt, wenn KEINE Spec verknuepft ist.
-- Wenn eine Spec verknuepft ist UND diese eine net_battery_capacity_kwh hat, ist die Spec
-- autoritativ - der User-Wert wuerde nur einen verwirrenden Doppel-State erzeugen.
--
-- Specs ohne net_battery_capacity_kwh (Bucket-D SONSTIGE_CUSTOM): customNet bleibt
-- als notwendiger Fallback erhalten, weil sonst die Cars keine Kapazitaets-Info haetten.

UPDATE car c
SET custom_net_capacity_kwh = NULL
WHERE c.vehicle_specification_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM vehicle_specification s
      WHERE s.id = c.vehicle_specification_id
        AND s.net_battery_capacity_kwh IS NOT NULL
  );
