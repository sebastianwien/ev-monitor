-- Live sources (Tesla Live, Smartcar, go-e Wallbox, EU Data Act sync) never report a route type.
-- Until 11.10.2026 the ingest copied the route type of the charge before onto them, so a single
-- "motorway" chosen in the form spread over every following automatic charge and skewed the
-- consumption per route type. HIGHWAY and CITY equal to the previous charge are therefore copies
-- and become unknown. Kept: COMBINED (reads as "normal driving, mix unknown"), values that differ
-- from the charge before (set on purpose) and the first charge of a car. Data-only change,
-- compatible with the running release.
UPDATE ev_log l
SET route_type = NULL
FROM (
    SELECT id, route_type,
           LAG(route_type) OVER (PARTITION BY car_id ORDER BY logged_at) AS previous_route_type
    FROM ev_log
    WHERE deleted_at IS NULL
) x
WHERE l.id = x.id
  AND l.data_source IN ('TESLA_LIVE', 'SMARTCAR_LIVE', 'WALLBOX_GOE', 'EU_DATA_ACT_SYNC')
  AND l.route_type IN ('HIGHWAY', 'CITY')
  AND x.previous_route_type = l.route_type;
