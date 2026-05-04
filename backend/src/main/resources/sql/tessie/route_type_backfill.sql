-- Derive ev_log.route_type for TESSIE charges of one car: dominant trip
-- route_type (by km) within the window between this charge and the previous
-- charge. Mirrors V101 step 6, parameterized for one car.
--
-- Parameters: :carId (uuid)
UPDATE ev_log el
SET route_type = classified.derived_route_type
FROM (
  WITH log_windows AS (
    SELECT
      l.id           AS log_id,
      l.car_id,
      l.logged_at    AS charge_start,
      LAG(l.logged_at) OVER (PARTITION BY l.car_id ORDER BY l.logged_at) AS prev_charge_start
    FROM ev_log l
    WHERE l.route_type IS NULL
      AND l.data_source = 'TESSIE'
      AND l.car_id = :carId
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
      WHEN highway_km > COALESCE(combined_km, 0) AND highway_km > COALESCE(city_km, 0)    THEN 'HIGHWAY'
      WHEN city_km    > COALESCE(combined_km, 0) AND city_km    > COALESCE(highway_km, 0) THEN 'CITY'
      ELSE 'COMBINED'
    END AS derived_route_type
  FROM trip_agg
) classified
WHERE el.id = classified.log_id
