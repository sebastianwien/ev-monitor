-- Merge consecutive Tessie charge sessions for one (user, vin) into chargers,
-- using a 6-minute gap + same-rounded-location heuristic. Returns merged rows
-- WITHOUT geohash or public/private classification - those are computed in
-- TessieProcessorService.java with ch.hsr.geohash.GeoHash (single source of truth).
--
-- Odometer is already in km: TessieClient fetches with distance_format=km.
-- Do NOT convert from miles here (the V101 Foxcar dump was miles, the API is not).
--
-- Parameters: :userId (uuid), :vin (text)
WITH raw AS (
  SELECT
    t.tessie_id,
    (t.raw->>'started_at')::bigint                          AS started_at_epoch,
    (t.raw->>'ended_at')::bigint                            AS ended_at_epoch,
    (t.raw->>'energy_added')::numeric                       AS energy_added_kwh,
    (t.raw->>'starting_battery')::int                       AS soc_start,
    (t.raw->>'ending_battery')::int                         AS soc_end,
    (t.raw->>'odometer')::numeric                          AS odometer_km,
    (t.raw->>'latitude')::numeric                           AS lat,
    (t.raw->>'longitude')::numeric                          AS lon,
    ROUND((t.raw->>'latitude')::numeric,  3)                AS lat_r,
    ROUND((t.raw->>'longitude')::numeric, 3)                AS lon_r,
    COALESCE((t.raw->>'is_supercharger')::boolean, false)   AS is_supercharger,
    COALESCE((t.raw->>'is_fast_charger')::boolean,  false)  AS is_fast_charger,
    NULLIF((t.raw->>'charger_power')::numeric, 0)           AS charger_power_kw
  FROM tessie_raw_imports t
  WHERE t.user_id = :userId
    AND t.vin = :vin
    AND t.type = 'charge'
    AND t.processed = false
),
with_gap AS (
  SELECT *,
    LEAD(started_at_epoch) OVER (ORDER BY started_at_epoch) - ended_at_epoch AS gap_to_next,
    LEAD(lat_r)            OVER (ORDER BY started_at_epoch)                  AS next_lat_r,
    LEAD(lon_r)            OVER (ORDER BY started_at_epoch)                  AS next_lon_r
  FROM raw
),
chained AS (
  SELECT *,
    SUM(CASE
      WHEN gap_to_next < 360 AND lat_r = next_lat_r AND lon_r = next_lon_r THEN 0
      ELSE 1
    END) OVER (ORDER BY tessie_id DESC) AS chain_id
  FROM with_gap
)
SELECT
  MIN(started_at_epoch)                                       AS started_at_epoch,
  MAX(ended_at_epoch)                                         AS ended_at_epoch,
  SUM(energy_added_kwh)                                       AS kwh_charged,
  (ARRAY_AGG(soc_start    ORDER BY started_at_epoch))[1]      AS soc_start,
  (ARRAY_AGG(soc_end      ORDER BY started_at_epoch DESC))[1] AS soc_end,
  (ARRAY_AGG(odometer_km  ORDER BY started_at_epoch DESC))[1] AS odometer_km,
  AVG(lat)                                                    AS lat,
  AVG(lon)                                                    AS lon,
  BOOL_OR(is_supercharger)                                    AS is_supercharger,
  BOOL_OR(is_fast_charger)                                    AS is_fast_charger,
  MAX(charger_power_kw)                                       AS charger_power_kw
FROM chained
GROUP BY chain_id
ORDER BY MIN(started_at_epoch)
