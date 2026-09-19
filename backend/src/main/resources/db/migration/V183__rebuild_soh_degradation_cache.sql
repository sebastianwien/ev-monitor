-- Charge-log SoH estimates backed by fewer than 5 charges are no longer shown: a single
-- charge carries the full systematic error of the estimate. The denormalized cache on car
-- still holds those values though, and it is what drives effective_battery_capacity_kwh in
-- every consumption and phantom-drain calculation. Rebuild it from the visible entries only.
--
-- Scoped to cars whose newest entry is exactly such a hidden estimate. battery_degradation_percent
-- can also be set by hand in the car form, on cars that have no SoH entry at all - a broader
-- update would silently wipe those values.
--
-- The SoH entries themselves are kept. They become visible again once enough charges back them.

WITH newest AS (
    SELECT DISTINCT ON (car_id) car_id, source, sample_size
    FROM car_battery_soh_log
    ORDER BY car_id, recorded_at DESC, created_at DESC
),
affected AS (
    SELECT car_id FROM newest
    WHERE source = 'CHARGE_LOG' AND (sample_size IS NULL OR sample_size < 5)
),
visible_latest AS (
    SELECT DISTINCT ON (car_id) car_id, soh_percent
    FROM car_battery_soh_log
    WHERE car_id IN (SELECT car_id FROM affected)
      AND (source <> 'CHARGE_LOG' OR (sample_size IS NOT NULL AND sample_size >= 5))
    ORDER BY car_id, recorded_at DESC, created_at DESC
)
UPDATE car c
SET battery_degradation_percent = (
        SELECT 100 - v.soh_percent FROM visible_latest v WHERE v.car_id = c.id
    ),
    updated_at = now()
WHERE c.id IN (SELECT car_id FROM affected);
