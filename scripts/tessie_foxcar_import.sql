-- =============================================================================
-- Tessie Foxcar Fleet Import
-- Charges -> ev_log, Drives -> ev_trip
-- Run once against ev_monitor DB (after V99 migration)
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 0. Geohash helper
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION geohash_encode(lat float, lon float, prec int DEFAULT 6)
RETURNS text AS $$
DECLARE
  chars     text    := '0123456789bcdefghjkmnpqrstuvwxyz';
  lat_min   float   := -90.0;  lat_max float := 90.0;
  lon_min   float   := -180.0; lon_max float := 180.0;
  result    text    := '';
  is_lon    bool    := true;
  bits      int     := 0;
  ch        int     := 0;
  mid       float;
BEGIN
  WHILE length(result) < prec LOOP
    IF is_lon THEN
      mid := (lon_min + lon_max) / 2;
      IF lon >= mid THEN ch := ch | (16 >> (bits % 5)); lon_min := mid;
      ELSE lon_max := mid; END IF;
    ELSE
      mid := (lat_min + lat_max) / 2;
      IF lat >= mid THEN ch := ch | (16 >> (bits % 5)); lat_min := mid;
      ELSE lat_max := mid; END IF;
    END IF;
    is_lon := NOT is_lon;
    bits   := bits + 1;
    IF bits % 5 = 0 THEN
      result := result || substr(chars, ch + 1, 1);
      ch := 0;
    END IF;
  END LOOP;
  RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- -----------------------------------------------------------------------------
-- 1. VIN-Mapping (Lookup via variant_name + net_kwh, keine hartkodierten UUIDs)
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE vin_meta (
  vin               text PRIMARY KEY,
  model             text,    -- MODEL_3 / MODEL_S / MODEL_X / MODEL_Y
  manufacture_year  int,
  spec_variant_name text,    -- vehicle_specification.variant_name (Lookup)
  spec_net_kwh      numeric, -- vehicle_specification.net_battery_capacity_kwh (Lookup)
  email_slug        text,    -- Prefix fuer @foxcar.de
  net_kwh           numeric  -- fuer car.battery_capacity_kwh
);

INSERT INTO vin_meta (vin, model, manufacture_year, spec_variant_name, spec_net_kwh, email_slug, net_kwh) VALUES
  ('XP7YGCEK8PB050228', 'MODEL_Y', 2023, 'Model Y LR AWD',           75.00, 'model_y_lr_awd_050228',        75.00),
  ('LRW3E7EK9MC342825', 'MODEL_3', 2021, 'Model 3 LR AWD',           70.00, 'model_3_lr_awd_342825',        70.00),
  ('LRW3E7EC8MC342999', 'MODEL_3', 2021, 'Model 3 LR AWD',           70.00, 'model_3_lr_awd_342999',        70.00),
  ('LRW3E7EL0NC560582', 'MODEL_3', 2022, 'Model 3 Performance',      75.00, 'model_3_performance_560582',   75.00),
  ('LRW3E7FS3PC674004', 'MODEL_3', 2023, 'Model 3 RWD Highland',     60.00, 'model_3_rwd_highland_674004',  60.00),
  ('LRW3E7FS4PC672729', 'MODEL_3', 2023, 'Model 3 RWD Highland',     60.00, 'model_3_rwd_highland_672729',  60.00),
  ('LRW3E7FS5PC674179', 'MODEL_3', 2023, 'Model 3 RWD Highland',     60.00, 'model_3_rwd_highland_674179',  60.00),
  ('LRW3E7FRXNC492178', 'MODEL_3', 2022, 'Model 3 RWD LFP',          57.00, 'model_3_rwd_lfp_492178',       57.00),
  ('LRW3E7FR6NC478911', 'MODEL_3', 2022, 'Model 3 RWD LFP',          57.00, 'model_3_rwd_lfp_478911',       57.00),
  ('5YJSA7H13FF099676', 'MODEL_S', 2015, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_099676',            80.80),
  ('5YJSA7E21FF112681', 'MODEL_S', 2015, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_112681',            80.80),
  ('5YJSA7H16EFP60602', 'MODEL_S', 2014, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_060602',            80.80),
  ('5YJSA3H14EFP44262', 'MODEL_S', 2014, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_044262',            80.80),
  ('5YJSA2DN6DFP18960', 'MODEL_S', 2013, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_018960',            80.80),
  ('5YJSA7H19EFP64949', 'MODEL_S', 2014, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_064949',            80.80),
  ('5YJSA6H19EFP57476', 'MODEL_S', 2014, 'Model S 85 (2013-2016)',   80.80, 'model_s_85_057476',            80.80),
  ('5YJXCCE25GF009659', 'MODEL_X', 2016, 'Model X 90D (2015-2016)', 85.00, 'model_x_90d_009659',           85.00);

-- -----------------------------------------------------------------------------
-- 2. Dummy-User anlegen (einer pro VIN)
-- -----------------------------------------------------------------------------
INSERT INTO app_user (
  id, email, auth_provider, role, username,
  email_verified, is_seed_data, email_notifications_enabled,
  leaderboard_visible, is_premium, referral_reward_given, trial_used,
  country, registration_locale, created_at, updated_at
)
SELECT
  gen_random_uuid(),
  m.email_slug || '@foxcar.de',
  'SYSTEM', 'USER',
  m.email_slug,
  true, false, false,
  false, false, false, false,
  'DE', 'de',
  NOW(), NOW()
FROM vin_meta m
ON CONFLICT (email) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. Cars anlegen (license_plate = VIN fuer Rueckverfolgbarkeit)
-- -----------------------------------------------------------------------------
INSERT INTO car (
  id, user_id, model, manufacture_year, license_plate,
  battery_capacity_kwh, vehicle_specification_id,
  status, is_primary, image_public, is_business_car, has_heat_pump,
  created_at, updated_at
)
SELECT
  gen_random_uuid(),
  u.id,
  m.model,
  m.manufacture_year,
  m.vin,
  m.net_kwh,
  (SELECT vs.id FROM vehicle_specification vs
   WHERE vs.variant_name = m.spec_variant_name
     AND vs.net_battery_capacity_kwh = m.spec_net_kwh
   LIMIT 1),
  'ACTIVE', true, false, true, false,
  NOW(), NOW()
FROM vin_meta m
JOIN app_user u ON u.email = m.email_slug || '@foxcar.de'
ON CONFLICT DO NOTHING;

-- Hilfstabelle: VIN -> car_id + user_id fuer spaetere Inserts
CREATE TEMP TABLE vin_car AS
SELECT m.vin, c.id AS car_id, c.user_id, m.net_kwh
FROM vin_meta m
JOIN app_user u ON u.email = m.email_slug || '@foxcar.de'
JOIN car c ON c.user_id = u.id AND c.license_plate = m.vin;

-- -----------------------------------------------------------------------------
-- 4. Charges -> ev_log  (Merge: <6min + gleicher Standort)
-- -----------------------------------------------------------------------------
WITH raw AS (
  SELECT
    t.vin,
    t.tessie_id,
    (t.raw->>'started_at')::bigint           AS started_at,
    (t.raw->>'ended_at')::bigint             AS ended_at,
    (t.raw->>'energy_added')::float          AS energy_added,
    (t.raw->>'starting_battery')::int        AS soc_start,
    (t.raw->>'ending_battery')::int          AS soc_end,
    (t.raw->>'odometer')::float * 1.60934    AS odometer_km,
    (t.raw->>'latitude')::float              AS lat,
    (t.raw->>'longitude')::float             AS lon,
    ROUND((t.raw->>'latitude')::numeric,  3) AS lat_r,
    ROUND((t.raw->>'longitude')::numeric, 3) AS lon_r,
    (t.raw->>'is_supercharger')::boolean     AS is_supercharger,
    (t.raw->>'is_fast_charger')::boolean     AS is_fast_charger
  FROM tessie_raw_imports t
  WHERE t.type = 'charge'
),
with_gap AS (
  SELECT *,
    LEAD(started_at) OVER (PARTITION BY vin ORDER BY started_at) - ended_at AS gap_to_next,
    LEAD(lat_r)      OVER (PARTITION BY vin ORDER BY started_at)            AS next_lat_r,
    LEAD(lon_r)      OVER (PARTITION BY vin ORDER BY started_at)            AS next_lon_r
  FROM raw
),
chained AS (
  SELECT *,
    SUM(CASE
      WHEN gap_to_next < 360 AND lat_r = next_lat_r AND lon_r = next_lon_r THEN 0
      ELSE 1
    END) OVER (PARTITION BY vin ORDER BY tessie_id DESC) AS chain_id
  FROM with_gap
),
merged AS (
  SELECT
    vin, chain_id,
    MIN(started_at)                                        AS started_at,
    MAX(ended_at)                                          AS ended_at,
    SUM(energy_added)                                      AS kwh_charged,
    (ARRAY_AGG(soc_start    ORDER BY started_at))[1]       AS soc_start,
    (ARRAY_AGG(soc_end      ORDER BY started_at DESC))[1]  AS soc_end,
    (ARRAY_AGG(odometer_km  ORDER BY started_at DESC))[1]  AS odometer_km,
    AVG(lat)                                               AS lat,
    AVG(lon)                                               AS lon,
    BOOL_OR(is_supercharger)                               AS is_supercharger,
    BOOL_OR(is_fast_charger)                               AS is_fast_charger
  FROM chained
  GROUP BY vin, chain_id
)
INSERT INTO ev_log (
  id, car_id,
  kwh_charged, charge_duration_minutes,
  logged_at, odometer_km,
  soc_start_percent, soc_after_charge_percent,
  geohash, is_public_charging, charging_type,
  data_source, measurement_type, include_in_statistics,
  created_at, updated_at
)
SELECT
  gen_random_uuid(),
  vc.car_id,
  ROUND(m.kwh_charged::numeric, 2),
  ROUND(((m.ended_at - m.started_at) / 60.0)::numeric)::int,
  TO_TIMESTAMP(m.started_at) AT TIME ZONE 'UTC',
  ROUND(m.odometer_km::numeric)::int,
  m.soc_start,
  m.soc_end,
  CASE
    WHEN NOT (m.is_supercharger OR m.is_fast_charger)
     AND (geohash_encode(m.lat, m.lon, 6) = 'u1h2vp'   -- Depot Stolberg
       OR geohash_encode(m.lat, m.lon, 6) = 'u1h2ff')  -- Depot Aachen
    THEN geohash_encode(m.lat, m.lon, 6)
    ELSE geohash_encode(m.lat, m.lon, 7)
  END,
  NOT (
    NOT (m.is_supercharger OR m.is_fast_charger)
    AND (geohash_encode(m.lat, m.lon, 6) = 'u1h2vp'
      OR geohash_encode(m.lat, m.lon, 6) = 'u1h2ff')
  ),
  CASE WHEN m.is_supercharger OR m.is_fast_charger THEN 'DC' ELSE 'AC' END,
  'TESSIE', 'AT_VEHICLE', true,
  NOW(), NOW()
FROM merged m
JOIN vin_car vc ON vc.vin = m.vin
ON CONFLICT (car_id, logged_at, data_source) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Drives -> ev_trip  (Merge: <6min; Filter: >=500m)
-- -----------------------------------------------------------------------------
WITH raw AS (
  SELECT
    t.vin,
    t.tessie_id,
    (t.raw->>'started_at')::bigint                  AS started_at,
    (t.raw->>'ended_at')::bigint                    AS ended_at,
    (t.raw->>'starting_battery')::float             AS soc_start,
    (t.raw->>'ending_battery')::float               AS soc_end,
    (t.raw->>'starting_odometer')::float * 1.60934  AS odo_start_km,
    (t.raw->>'ending_odometer')::float  * 1.60934   AS odo_end_km,
    (t.raw->>'odometer_distance')::float * 1.60934  AS distance_km,
    (t.raw->>'energy_used')::float                  AS energy_kwh,
    (t.raw->>'starting_latitude')::float            AS lat_start,
    (t.raw->>'starting_longitude')::float           AS lon_start,
    (t.raw->>'ending_latitude')::float              AS lat_end,
    (t.raw->>'ending_longitude')::float             AS lon_end,
    (t.raw->>'average_outside_temperature')::float  AS temp_celsius,
    (t.raw->>'average_speed')::float                AS average_speed
  FROM tessie_raw_imports t
  WHERE t.type = 'drive'
),
with_gap AS (
  SELECT *,
    LEAD(started_at) OVER (PARTITION BY vin ORDER BY started_at) - ended_at AS gap_to_next
  FROM raw
),
chained AS (
  SELECT *,
    SUM(CASE WHEN gap_to_next >= 360 OR gap_to_next IS NULL THEN 1 ELSE 0 END)
      OVER (PARTITION BY vin ORDER BY tessie_id DESC) AS chain_id
  FROM with_gap
),
merged AS (
  SELECT
    vin, chain_id,
    MIN(started_at)                                          AS started_at,
    MAX(ended_at)                                            AS ended_at,
    (ARRAY_AGG(soc_start    ORDER BY started_at))[1]         AS soc_start,
    (ARRAY_AGG(soc_end      ORDER BY started_at DESC))[1]    AS soc_end,
    (ARRAY_AGG(odo_start_km ORDER BY started_at))[1]         AS odo_start_km,
    (ARRAY_AGG(odo_end_km   ORDER BY started_at DESC))[1]    AS odo_end_km,
    SUM(distance_km)                                         AS distance_km,
    SUM(energy_kwh)                                          AS energy_kwh,
    (ARRAY_AGG(lat_start    ORDER BY started_at))[1]         AS lat_start,
    (ARRAY_AGG(lon_start    ORDER BY started_at))[1]         AS lon_start,
    (ARRAY_AGG(lat_end      ORDER BY started_at DESC))[1]    AS lat_end,
    (ARRAY_AGG(lon_end      ORDER BY started_at DESC))[1]    AS lon_end,
    AVG(temp_celsius)                                        AS temp_celsius,
    SUM(average_speed * distance_km) /
      NULLIF(SUM(distance_km), 0)                          AS weighted_avg_speed
  FROM chained
  GROUP BY vin, chain_id
  HAVING SUM(distance_km) >= 0.5
),
-- SoH-adjustierte Kapazitaet pro Auto: Median der letzten 5 qualifizierenden Ladevorgaenge
-- (identische Logik wie BatterySohAutoDetector: AT_VEHICLE + SoC-Delta >= 30%)
effective_capacity AS (
  SELECT car_id, PERCENTILE_CONT(0.5) WITHIN GROUP (
    ORDER BY kwh_charged / NULLIF(soc_after_charge_percent - soc_start_percent, 0) * 100
  ) AS median_capacity_kwh
  FROM (
    SELECT el.car_id, el.kwh_charged, el.soc_start_percent, el.soc_after_charge_percent,
           ROW_NUMBER() OVER (PARTITION BY el.car_id ORDER BY el.logged_at DESC) AS rn
    FROM ev_log el
    JOIN vin_car vc ON vc.car_id = el.car_id
    WHERE el.measurement_type = 'AT_VEHICLE'
      AND el.soc_start_percent IS NOT NULL
      AND el.soc_after_charge_percent IS NOT NULL
      AND (el.soc_after_charge_percent - el.soc_start_percent) >= 30
      AND el.kwh_charged IS NOT NULL AND el.kwh_charged > 0
      AND el.include_in_statistics = true
  ) ranked
  WHERE rn <= 5
  GROUP BY car_id
)
INSERT INTO ev_trip (
  user_id, car_id, data_source,
  trip_started_at, trip_ended_at,
  soc_start, soc_end,
  odometer_start_km, odometer_end_km, distance_km,
  estimated_consumed_kwh, nominal_full_pack_kwh,
  location_start_geohash, location_end_geohash,
  outside_temp_celsius, route_type,
  status, created_at
)
SELECT
  vc.user_id, vc.car_id, 'TESSIE',
  TO_TIMESTAMP(m.started_at),
  TO_TIMESTAMP(m.ended_at),
  m.soc_start, m.soc_end,
  ROUND(m.odo_start_km::numeric, 1),
  ROUND(m.odo_end_km::numeric,   1),
  ROUND(m.distance_km::numeric,  1),
  ROUND(m.energy_kwh::numeric,   2),
  ROUND(COALESCE(ec.median_capacity_kwh, vc.net_kwh)::numeric, 2),
  geohash_encode(m.lat_start, m.lon_start, 6),
  geohash_encode(m.lat_end,   m.lon_end,   6),
  ROUND(m.temp_celsius::numeric, 1),
  CASE
    WHEN m.weighted_avg_speed >= 90 THEN 'HIGHWAY'
    WHEN m.weighted_avg_speed <  60 THEN 'CITY'
    WHEN m.weighted_avg_speed IS NOT NULL THEN 'COMBINED'
    ELSE NULL
  END,
  'COMPLETED', NOW()
FROM merged m
JOIN vin_car vc ON vc.vin = m.vin
LEFT JOIN effective_capacity ec ON ec.car_id = vc.car_id;

-- -----------------------------------------------------------------------------
-- 6. ev_log.route_type aus ev_trip ableiten (dominanter Streckentyp nach km)
-- -----------------------------------------------------------------------------
UPDATE ev_log el
SET route_type = classified.derived_route_type
FROM (
  WITH log_windows AS (
    SELECT
      el.id   AS log_id,
      el.car_id,
      el.logged_at AS charge_start,
      LAG(el.logged_at) OVER (PARTITION BY el.car_id ORDER BY el.logged_at) AS prev_charge_start
    FROM ev_log el
    WHERE el.route_type IS NULL AND el.data_source = 'TESSIE'
  ),
  trip_agg AS (
    SELECT
      lw.log_id,
      SUM(et.distance_km) FILTER (WHERE et.route_type = 'HIGHWAY')  AS highway_km,
      SUM(et.distance_km) FILTER (WHERE et.route_type = 'COMBINED') AS combined_km,
      SUM(et.distance_km) FILTER (WHERE et.route_type = 'CITY')     AS city_km
    FROM log_windows lw
    JOIN ev_trip et ON et.car_id = lw.car_id
      AND et.route_type IS NOT NULL
      AND et.trip_ended_at <= lw.charge_start
      AND et.trip_started_at >= COALESCE(lw.prev_charge_start, lw.charge_start - interval '30 days')
    GROUP BY lw.log_id
  )
  SELECT
    log_id,
    CASE
      WHEN highway_km  > COALESCE(combined_km, 0) AND highway_km  > COALESCE(city_km, 0) THEN 'HIGHWAY'
      WHEN city_km     > COALESCE(combined_km, 0) AND city_km     > COALESCE(highway_km, 0) THEN 'CITY'
      ELSE 'COMBINED'
    END AS derived_route_type
  FROM trip_agg
) classified
WHERE el.id = classified.log_id;

-- -----------------------------------------------------------------------------
-- 7. SoH-Detection triggern (nach COMMIT ausfuehren):
--    curl -X POST https://ev-monitor.net/api/admin/soh/redetect \
--         -H "Authorization: Bearer $ADMIN_TOKEN"
-- Erkennt SoH fuer alle Autos mit AT_VEHICLE Logs ohne SoH-Eintrag im aktuellen Jahr.
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 8. Processed-Flag setzen
-- -----------------------------------------------------------------------------
UPDATE tessie_raw_imports SET processed = true
WHERE type IN ('charge', 'drive');

DROP FUNCTION IF EXISTS geohash_encode(float, float, int);

COMMIT;
