-- Seed WLTP data for popular EV models
-- Phase 8: Initial WLTP dataset
-- Source: Official manufacturer WLTP figures (COMBINED cycle, rounded)
-- ON CONFLICT DO NOTHING = safe to re-run, won't overwrite community contributions

INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    -- === TESLA ===
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 57.5, 358, 16.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 75.0, 560, 15.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 79.0, 528, 17.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_Y', 75.0, 533, 16.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_Y', 79.0, 514, 17.5, 'COMBINED', NOW(), NOW()),

    -- === VW ===
    (gen_random_uuid(), 'VW', 'ID_3', 45.0, 342, 14.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_3', 58.0, 427, 15.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_3', 77.0, 549, 15.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_4', 52.0, 341, 17.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_4', 77.0, 522, 17.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_5', 77.0, 516, 17.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_7', 77.0, 621, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_7', 86.0, 696, 14.2, 'COMBINED', NOW(), NOW()),

    -- === HYUNDAI & KIA ===
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 58.0, 384, 17.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 77.4, 507, 16.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 53.0, 429, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 77.4, 614, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'KONA_ELECTRIC', 39.2, 305, 14.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'KONA_ELECTRIC', 64.8, 514, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'KIA', 'EV_6', 58.0, 394, 17.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'KIA', 'EV_6', 77.4, 528, 16.2, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'KIA', 'EV_9', 99.8, 563, 20.2, 'COMBINED', NOW(), NOW()),

    -- === BMW ===
    (gen_random_uuid(), 'BMW', 'I4', 80.7, 590, 15.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX3', 74.0, 460, 18.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BMW', 'IX3', 80.0, 499, 17.8, 'COMBINED', NOW(), NOW()),

    -- === AUDI ===
    (gen_random_uuid(), 'AUDI', 'Q4_E_TRON', 52.0, 341, 17.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'Q4_E_TRON', 77.0, 522, 17.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'Q4_E_TRON', 82.0, 534, 17.7, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'AUDI', 'E_TRON', 95.0, 446, 24.2, 'COMBINED', NOW(), NOW()),

    -- === SKODA ===
    (gen_random_uuid(), 'SKODA', 'ENYAQ', 77.0, 534, 16.5, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'SKODA', 'ENYAQ', 82.0, 567, 16.6, 'COMBINED', NOW(), NOW()),

    -- === POLESTAR ===
    (gen_random_uuid(), 'POLESTAR', 'POLESTAR_2', 78.0, 635, 13.9, 'COMBINED', NOW(), NOW()),

    -- === MG ===
    (gen_random_uuid(), 'MG', 'MG4', 64.0, 435, 16.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MG', 'MG_ZS_EV', 51.0, 320, 18.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'MG', 'MG_ZS_EV', 72.6, 440, 18.0, 'COMBINED', NOW(), NOW()),

    -- === RENAULT ===
    (gen_random_uuid(), 'RENAULT', 'ZOE', 41.0, 316, 14.6, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'RENAULT', 'ZOE', 52.0, 395, 14.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'RENAULT', 'MEGANE_E_TECH', 40.0, 300, 15.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'RENAULT', 'MEGANE_E_TECH', 60.0, 450, 15.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'RENAULT', 'RENAULT_5', 40.0, 300, 15.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'RENAULT', 'RENAULT_5', 52.0, 400, 14.5, 'COMBINED', NOW(), NOW()),

    -- === NISSAN ===
    (gen_random_uuid(), 'NISSAN', 'LEAF', 40.0, 270, 16.8, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'NISSAN', 'LEAF', 62.0, 385, 18.0, 'COMBINED', NOW(), NOW()),

    -- === OPEL ===
    (gen_random_uuid(), 'OPEL', 'MOKKA_E', 50.0, 324, 17.5, 'COMBINED', NOW(), NOW()),

    -- === FIAT ===
    (gen_random_uuid(), 'FIAT', 'FIAT_500E', 21.3, 150, 15.0, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'FIAT', 'FIAT_500E', 42.0, 320, 14.5, 'COMBINED', NOW(), NOW()),

    -- === DACIA ===
    (gen_random_uuid(), 'DACIA', 'DACIA_SPRING', 26.8, 220, 13.9, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'DACIA', 'DACIA_SPRING', 33.0, 267, 13.9, 'COMBINED', NOW(), NOW()),

    -- === BYD ===
    (gen_random_uuid(), 'BYD', 'BYD_SEAL', 61.4, 420, 16.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'BYD', 'BYD_SEAL', 82.5, 570, 15.9, 'COMBINED', NOW(), NOW())

ON CONFLICT ON CONSTRAINT uq_vehicle_spec DO NOTHING;
