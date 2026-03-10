-- Fix XPENG G6 battery variants:
-- 80.8 kWh was incorrectly entered; the real Chinese variant is 80.0 kWh.
-- Migrate the one affected car and remove the bogus spec.

UPDATE car
SET battery_capacity_kwh = 80.0,
    updated_at            = NOW()
WHERE model                = 'XPENG_G6'
  AND battery_capacity_kwh = 80.8;

DELETE FROM vehicle_specification
WHERE car_model            = 'XPENG_G6'
  AND battery_capacity_kwh = 80.8;
