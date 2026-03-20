-- Remove backend grouping for SpritMonitor imports.
-- Grouping is now handled client-side (same as manual Nachladen entries).

-- 1. Release all SpritMonitor sub-sessions from their groups
UPDATE ev_log
SET session_group_id = NULL
WHERE session_group_id IN (
    SELECT id FROM charging_session_group WHERE data_source = 'SPRITMONITOR_IMPORT'
);

-- 2. Delete the now-orphaned SpritMonitor groups
DELETE FROM charging_session_group WHERE data_source = 'SPRITMONITOR_IMPORT';
