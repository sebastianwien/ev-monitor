-- Fix ev_log entries where session_group_id is NULL despite belonging to a session group.
--
-- Root cause: an earlier code version used Propagation.REQUIRES_NEW for session grouping,
-- which ran in a separate transaction before the ev_log was committed. The setSessionGroupId
-- UPDATE matched 0 rows (ev_log not yet visible in DB), leaving session_group_id = NULL
-- while the session group entity was created successfully.
--
-- The timestamp between the ev_log and the session group may differ slightly (up to a few
-- seconds, e.g. due to processing time between the log timestamp and when the group entity
-- is created). We match within a ±5 minute window around session_start.
--
-- Extra safety: for session_count=1 groups we also require kwh_charged to match exactly,
-- since the group's total_kwh_charged equals the single sub-session's kWh.

UPDATE ev_log
SET
    session_group_id = (
        SELECT csg.id
        FROM charging_session_group csg
        WHERE csg.car_id = ev_log.car_id
          AND csg.data_source = ev_log.data_source
          AND ABS(EXTRACT(EPOCH FROM (ev_log.logged_at - csg.session_start))) < 300
          AND (
              csg.session_count > 1
              OR ev_log.kwh_charged = csg.total_kwh_charged
          )
        ORDER BY ABS(EXTRACT(EPOCH FROM (ev_log.logged_at - csg.session_start)))
        LIMIT 1
    ),
    include_in_statistics = false
WHERE ev_log.session_group_id IS NULL
  AND ev_log.data_source IN ('WALLBOX_GOE', 'API_UPLOAD')
  AND EXISTS (
      SELECT 1
      FROM charging_session_group csg
      WHERE csg.car_id = ev_log.car_id
        AND csg.data_source = ev_log.data_source
        AND ABS(EXTRACT(EPOCH FROM (ev_log.logged_at - csg.session_start))) < 300
        AND (
            csg.session_count > 1
            OR ev_log.kwh_charged = csg.total_kwh_charged
        )
  );
