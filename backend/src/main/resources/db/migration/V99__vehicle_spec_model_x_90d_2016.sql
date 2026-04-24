INSERT INTO vehicle_specification (
    id, car_brand, car_model, battery_capacity_kwh, net_battery_capacity_kwh,
    official_range_km, official_consumption_kwh_per_100km,
    wltp_type, rating_source, variant_name, available_from, available_to,
    created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'TESLA', 'MODEL_X',
    90.00, 85.00,
    413.00, 21.80,
    'COMBINED', 'WLTP', 'Model X 90D (2015-2016)',
    '2015-09-01', '2017-03-31',
    NOW(), NOW()
);
