-- Activate WALLBOX_GOE and API_UPLOAD sub-sessions that were hidden in session groups.
-- They are now full ev_log entries contributing to statistics directly.
-- The charging_session_group table is kept for data safety but no longer used by the application.
UPDATE ev_log
SET include_in_statistics = true,
    session_group_id = NULL
WHERE session_group_id IS NOT NULL;
