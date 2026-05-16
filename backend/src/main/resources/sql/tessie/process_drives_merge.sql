-- Merge consecutive Tessie drive sessions for one (user, vin) into trips,
-- using a 6-minute gap heuristic. Filters out merged trips below 0.5 km
-- (Mikro-Trips: parking-lot manoeuvres, false positives). Returns merged rows
-- WITHOUT geohash - geohash is computed in TessieProcessorService.java.
--
-- Parameters: :userId (uuid), :vin (text)
WITH raw AS (
  SELECT
    t.tessie_id,
    (t.raw->>'started_at')::bigint                    AS started_at_epoch,
    (t.raw->>'ended_at')::bigint                      AS ended_at_epoch,
    (t.raw->>'starting_battery')::numeric             AS soc_start,
    (t.raw->>'ending_battery')::numeric               AS soc_end,
    (t.raw->>'starting_odometer')::numeric * 1.60934  AS odo_start_km,
    (t.raw->>'ending_odometer')::numeric  * 1.60934   AS odo_end_km,
    (t.raw->>'odometer_distance')::numeric * 1.60934  AS distance_km,
    (t.raw->>'energy_used')::numeric                  AS energy_kwh,
    (t.raw->>'starting_latitude')::numeric            AS lat_start,
    (t.raw->>'starting_longitude')::numeric           AS lon_start,
    (t.raw->>'ending_latitude')::numeric              AS lat_end,
    (t.raw->>'ending_longitude')::numeric             AS lon_end,
    (t.raw->>'average_outside_temperature')::numeric  AS temp_celsius,
    (t.raw->>'average_speed')::numeric                AS average_speed,
    (t.raw->>'max_speed')::numeric                    AS max_speed
  FROM tessie_raw_imports t
  WHERE t.user_id = :userId
    AND t.vin = :vin
    AND t.type = 'drive'
    AND t.processed = false
),
with_gap AS (
  SELECT *,
    LEAD(started_at_epoch) OVER (ORDER BY started_at_epoch) - ended_at_epoch AS gap_to_next
  FROM raw
),
chained AS (
  SELECT *,
    SUM(CASE WHEN gap_to_next >= 360 OR gap_to_next IS NULL THEN 1 ELSE 0 END)
      OVER (ORDER BY tessie_id DESC) AS chain_id
  FROM with_gap
)
SELECT
  MIN(started_at_epoch)                                          AS started_at_epoch,
  MAX(ended_at_epoch)                                            AS ended_at_epoch,
  (ARRAY_AGG(soc_start    ORDER BY started_at_epoch))[1]         AS soc_start,
  (ARRAY_AGG(soc_end      ORDER BY started_at_epoch DESC))[1]    AS soc_end,
  (ARRAY_AGG(odo_start_km ORDER BY started_at_epoch))[1]         AS odo_start_km,
  (ARRAY_AGG(odo_end_km   ORDER BY started_at_epoch DESC))[1]    AS odo_end_km,
  SUM(distance_km)                                               AS distance_km,
  SUM(energy_kwh)                                                AS energy_kwh,
  (ARRAY_AGG(lat_start    ORDER BY started_at_epoch))[1]         AS lat_start,
  (ARRAY_AGG(lon_start    ORDER BY started_at_epoch))[1]         AS lon_start,
  (ARRAY_AGG(lat_end      ORDER BY started_at_epoch DESC))[1]    AS lat_end,
  (ARRAY_AGG(lon_end      ORDER BY started_at_epoch DESC))[1]    AS lon_end,
  AVG(temp_celsius)                                              AS temp_celsius,
  CASE WHEN SUM(distance_km) > 0
       THEN SUM(average_speed * distance_km) / SUM(distance_km)
  END                                                            AS weighted_avg_speed,
  MAX(max_speed)                                                 AS max_speed
FROM chained
GROUP BY chain_id
HAVING SUM(distance_km) >= 0.5
ORDER BY MIN(started_at_epoch)
