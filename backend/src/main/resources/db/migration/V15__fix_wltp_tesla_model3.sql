-- Fix incorrect WLTP data for Tesla Model 3 75 kWh
-- Old data: 560 km, 15.5 kWh/100km (implied battery: 86.8 kWh - clearly wrong)
-- Official WLTP: 576 km, 14.0 kWh/100km (Long Range AWD)
UPDATE vehicle_specification
SET wltp_range_km                  = 576,
    wltp_consumption_kwh_per_100km = 14.0,
    updated_at                     = NOW()
WHERE car_brand = 'TESLA'
  AND car_model = 'MODEL_3'
  AND battery_capacity_kwh = 75.0
  AND wltp_range_km = 560
  AND wltp_consumption_kwh_per_100km = 15.5;
