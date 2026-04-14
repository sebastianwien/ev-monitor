-- EPA seed data for US market.
-- Source: fueleconomy.gov (2024 MY unless noted), combined cycle.
-- wltp_range_km holds EPA range converted: miles × 1.60934
-- wltp_consumption_kwh_per_100km holds EPA combE converted: kWh/100mi ÷ 1.60934
-- combE includes AC charging losses (~10-15%) — expected to be slightly higher than WLTP figures.
-- rating_source = 'EPA' — EU users never see this data (PublicModelService filters by source).
-- Capacities follow CarBrand.java enum caps (may differ slightly from EPA-listed usable kWh).
-- ON CONFLICT DO NOTHING ensures safe re-runs.

INSERT INTO vehicle_specification
    (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, rating_source, created_at, updated_at)
VALUES

    -- === TESLA (2024 MY) ===
    -- Model 3 Highland: 272/342/303 mi, 25.45/25.84/30.10 kWh/100mi
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 57.5, 438,  15.8, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 75.0, 550,  16.1, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 79.0, 488,  18.7, 'COMBINED', 'EPA', NOW(), NOW()),  -- Performance
    -- Model Y: 260/310/279 mi, 28.01/28.70/32.21 kWh/100mi
    (gen_random_uuid(), 'TESLA', 'MODEL_Y', 60.0, 418,  17.4, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_Y', 75.0, 499,  17.8, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'TESLA', 'MODEL_Y', 79.0, 449,  20.0, 'COMBINED', 'EPA', NOW(), NOW()),  -- Performance
    -- Model S Dual Motor (current gen, 100 kWh total / ~95 kWh usable): 402 mi, 27.70 kWh/100mi
    (gen_random_uuid(), 'TESLA', 'MODEL_S', 95.0, 647,  17.2, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Model S Plaid: 359 mi, 31.47 kWh/100mi
    (gen_random_uuid(), 'TESLA', 'MODEL_S_PLAID', 95.0, 578,  19.6, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Model X Dual Motor: 335 mi, 33.61 kWh/100mi
    (gen_random_uuid(), 'TESLA', 'MODEL_X', 95.0, 539,  20.9, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === FORD (2024 MY) ===
    -- Mustang Mach-E: SR RWD 250mi / ER RWD 320mi / ER AWD 300mi
    (gen_random_uuid(), 'FORD', 'MUSTANG_MACH_E',  68.0, 402,  20.5, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'FORD', 'MUSTANG_MACH_E',  88.0, 515,  19.8, 'COMBINED', 'EPA', NOW(), NOW()),  -- ER RWD
    (gen_random_uuid(), 'FORD', 'MUSTANG_MACH_E',  91.0, 483,  21.2, 'COMBINED', 'EPA', NOW(), NOW()),  -- ER AWD (91 kWh = CarBrand cap for AWD variant)
    -- F-150 Lightning: SR 240mi / ER 320mi
    (gen_random_uuid(), 'FORD', 'F_150_LIGHTNING',  98.0, 386,  30.7, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'FORD', 'F_150_LIGHTNING', 131.0, 515,  29.8, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === CHEVROLET ===
    -- Bolt EV (2023 MY refreshed): 259 mi, 28.11 kWh/100mi
    (gen_random_uuid(), 'CHEVROLET', 'BOLT_EV',     65.0, 417,  17.5, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Bolt EUV (2023 MY): 247 mi, 29.40 kWh/100mi
    (gen_random_uuid(), 'CHEVROLET', 'BOLT_EUV',    65.0, 398,  18.3, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Blazer EV RWD (2024 MY): 324 mi, 36.63 kWh/100mi
    (gen_random_uuid(), 'CHEVROLET', 'BLAZER_EV',   85.0, 521,  22.8, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Equinox EV FWD (2024 MY): 319 mi, 31.10 kWh/100mi (85 kWh = CarBrand cap; EPA tested 82 kWh usable)
    (gen_random_uuid(), 'CHEVROLET', 'EQUINOX_EV',  85.0, 513,  19.3, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === GMC ===
    -- Hummer EV Pickup 3X (2024 MY): 314 mi, 63.41 kWh/100mi (200 kWh = CarBrand cap for 3X/Edition 1)
    (gen_random_uuid(), 'GMC', 'HUMMER_EV',         200.0, 505, 39.4, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Hummer EV SUV (2024 MY): same platform/rating as pickup
    (gen_random_uuid(), 'GMC', 'HUMMER_EV_SUV',     200.0, 505, 39.4, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Sierra EV Denali Edition 1 (2025 MY): 390 mi, 52.36 kWh/100mi
    (gen_random_uuid(), 'GMC', 'SIERRA_EV',         200.0, 628, 32.5, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === CADILLAC ===
    -- Lyriq RWD (2025 MY): 326 mi, 36.01 kWh/100mi (102 kWh = CarBrand cap; EPA tested 95 kWh usable)
    (gen_random_uuid(), 'CADILLAC', 'LYRIQ',        102.0, 525, 22.4, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Optiq RWD (2026 MY, first EPA submission): 317 mi, 32.08 kWh/100mi
    (gen_random_uuid(), 'CADILLAC', 'OPTIQ',         85.0, 510, 19.9, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === DODGE ===
    -- Charger Daytona R/T AWD (2024 MY): 274 mi, 39.00 kWh/100mi
    (gen_random_uuid(), 'DODGE', 'CHARGER_DAYTONA', 100.5, 441, 24.2, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === JEEP ===
    -- Wagoneer S AWD (2024 MY): 303 mi, 34.63 kWh/100mi
    (gen_random_uuid(), 'JEEP', 'WAGONEER_S',       100.0, 488, 21.5, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === RIVIAN (2024 MY) ===
    -- R1T: Explore/Standard 270mi, Adventure/Large 352mi, Max Range 410mi
    (gen_random_uuid(), 'RIVIAN', 'R1T', 105.0, 435, 28.1, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'RIVIAN', 'R1T', 135.0, 567, 26.9, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'RIVIAN', 'R1T', 149.0, 660, 24.8, 'COMBINED', 'EPA', NOW(), NOW()),
    -- R1S: Explore/Standard 270mi, Adventure/Large 352mi, Max Range 400mi
    (gen_random_uuid(), 'RIVIAN', 'R1S', 105.0, 435, 28.1, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'RIVIAN', 'R1S', 135.0, 567, 26.9, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'RIVIAN', 'R1S', 149.0, 644, 25.4, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === LUCID (2024 MY) ===
    -- Air Pure 419mi, Grand Touring 516mi, Sapphire 427mi
    (gen_random_uuid(), 'LUCID', 'AIR',  88.0, 674, 15.3, 'COMBINED', 'EPA', NOW(), NOW()),   -- Pure
    (gen_random_uuid(), 'LUCID', 'AIR', 112.0, 830, 16.2, 'COMBINED', 'EPA', NOW(), NOW()),   -- Grand Touring
    (gen_random_uuid(), 'LUCID', 'AIR', 118.0, 687, 20.0, 'COMBINED', 'EPA', NOW(), NOW()),   -- Sapphire

    -- === HONDA (2024 MY) ===
    -- Prologue FWD: 296 mi, 33.97 kWh/100mi (GM Ultium platform)
    (gen_random_uuid(), 'HONDA', 'PROLOGUE', 85.0, 476, 21.1, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === HYUNDAI (2024 MY EPA — EU users keep existing WLTP data) ===
    -- Ioniq 5 SR RWD: 220mi | Ioniq 5 LR RWD: 303mi
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 58.0, 354, 19.3, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 77.4, 488, 18.6, 'COMBINED', 'EPA', NOW(), NOW()),
    -- Ioniq 6 SR RWD: 240mi | Ioniq 6 LR RWD: 361mi
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 53.0, 386, 15.5, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_6', 77.4, 581, 14.9, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === KIA (2024 MY EPA) ===
    -- EV6 SR RWD: 232mi | EV6 LR RWD: 310mi
    (gen_random_uuid(), 'KIA', 'EV_6', 58.0, 373, 18.0, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'KIA', 'EV_6', 77.4, 499, 18.0, 'COMBINED', 'EPA', NOW(), NOW()),
    -- EV9 Standard RWD: 230mi | EV9 Long Range RWD: 304mi
    (gen_random_uuid(), 'KIA', 'EV_9', 76.1, 370, 23.6, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'KIA', 'EV_9', 99.8, 489, 23.6, 'COMBINED', 'EPA', NOW(), NOW()),

    -- === VOLKSWAGEN (2024 MY EPA) ===
    -- ID.4 Standard RWD: 206mi | ID.4 Pro RWD: 291mi
    (gen_random_uuid(), 'VW', 'ID_4', 52.0, 332, 19.6, 'COMBINED', 'EPA', NOW(), NOW()),
    (gen_random_uuid(), 'VW', 'ID_4', 77.0, 468, 18.5, 'COMBINED', 'EPA', NOW(), NOW())

ON CONFLICT ON CONSTRAINT uq_vehicle_spec DO NOTHING;
