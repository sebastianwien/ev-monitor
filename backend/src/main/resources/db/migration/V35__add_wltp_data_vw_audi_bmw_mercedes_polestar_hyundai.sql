INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    -- VW
    (gen_random_uuid(), 'VW', 'ID_4', 82.0, 554, 14.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_BUZZ', 79.0, 423, 18.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_BUZZ', 86.0, 461, 18.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'E_UP', 32.3, 260, 12.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'E_GOLF', 35.8, 231, 15.5, 'COMBINED', NOW(), NOW()),
    -- Audi (alle Netto-Werte; A6/Q6 100.0 kWh und Q8 106.0 kWh sollten noch verifiziert werden)
    (gen_random_uuid(), 'AUDI', 'E_TRON', 71.0, 286, 24.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'E_TRON_GT', 93.4, 488, 19.1, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'A6_E_TRON', 83.0, 756, 11.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'A6_E_TRON', 100.0, 700, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'Q6_E_TRON', 83.0, 625, 13.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'Q6_E_TRON', 100.0, 598, 16.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'Q8_E_TRON', 95.0, 491, 19.4, 'COMBINED', NOW(), NOW()),
    -- Q8 e-tron: 106.0 kWh netto (war 114.0 kWh brutto)
    (gen_random_uuid(), 'AUDI', 'Q8_E_TRON', 106.0, 582, 19.6, 'COMBINED', NOW(), NOW()),
    -- BMW
    (gen_random_uuid(), 'BMW', 'I3', 42.2, 310, 13.6, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'I4', 67.0, 483, 13.9, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'I5', 81.2, 582, 14.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'I7', 101.7, 625, 16.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX', 76.6, 425, 18.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX', 105.2, 630, 16.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX1', 64.7, 440, 14.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX2', 64.7, 449, 14.4, 'COMBINED', NOW(), NOW()),
    -- Mercedes (alle Netto-Werte; EQA/EQB haben eine Batterie: 66.5 kWh netto)
    (gen_random_uuid(), 'MERCEDES', 'EQA', 66.5, 426, 15.6, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQB', 66.5, 419, 15.9, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQC', 80.0, 400, 20.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQE', 89.0, 659, 13.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQE', 90.6, 550, 16.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQE_SUV', 90.6, 590, 15.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQE_SUV', 96.0, 490, 19.6, 'COMBINED', NOW(), NOW()),
    -- EQS/EQS SUV: 107.8/108.4 kWh netto (118.0 kWh war Bruttowert, entfernt)
    (gen_random_uuid(), 'MERCEDES', 'EQS', 107.8, 770, 14.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQS_SUV', 108.4, 660, 16.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MERCEDES', 'EQV', 90.0, 418, 21.5, 'COMBINED', NOW(), NOW()),
    -- Polestar (107.0 kWh evtl. Brutto — verifizieren)
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_2', 67.0, 440, 15.2, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_2', 75.0, 560, 13.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_2', 82.0, 635, 12.9, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_3', 107.0, 610, 17.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_4', 94.0, 590, 15.9, 'COMBINED', NOW(), NOW()),
    -- Hyundai (63.0 kWh Ioniq 6 evtl. Brutto — verifizieren)
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 84.0, 601, 14.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 63.0, 430, 14.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 84.0, 614, 13.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_ELECTRIC', 38.3, 311, 12.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'KONA_ELECTRIC', 48.6, 377, 12.9, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'KONA_ELECTRIC', 65.4, 514, 12.7, 'COMBINED', NOW(), NOW())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, wltp_type) DO NOTHING;
