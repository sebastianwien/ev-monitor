ALTER TABLE ev_trip ADD COLUMN IF NOT EXISTS route_type VARCHAR(20);

-- Backfill for existing TESSIE trips: weighted average speed from raw drive segments
UPDATE ev_trip et
SET route_type = speeds.route_type
FROM (
    SELECT
        et.id AS trip_id,
        CASE
            WHEN SUM((r.raw->>'average_speed')::float * (r.raw->>'odometer_distance')::float) /
                 NULLIF(SUM((r.raw->>'odometer_distance')::float), 0) >= 90 THEN 'HIGHWAY'
            WHEN SUM((r.raw->>'average_speed')::float * (r.raw->>'odometer_distance')::float) /
                 NULLIF(SUM((r.raw->>'odometer_distance')::float), 0) < 60  THEN 'CITY'
            ELSE 'COMBINED'
        END AS route_type
    FROM ev_trip et
    JOIN car c ON c.id = et.car_id
    JOIN tessie_raw_imports r ON r.vin = c.license_plate
        AND r.type = 'drive'
        AND (r.raw->>'average_speed') IS NOT NULL
        AND (r.raw->>'odometer_distance')::float > 0.5
        AND TO_TIMESTAMP((r.raw->>'started_at')::bigint) >= et.trip_started_at - interval '1 minute'
        AND TO_TIMESTAMP((r.raw->>'ended_at')::bigint)   <= et.trip_ended_at   + interval '1 minute'
    WHERE et.data_source = 'TESSIE'
    GROUP BY et.id
    HAVING SUM((r.raw->>'odometer_distance')::float) > 0
) speeds
WHERE et.id = speeds.trip_id;
