-- Manual fix: Mark seed data as excluded from statistics
-- Run this if migration V14 didn't apply the UPDATEs correctly

-- Mark Tesla imports as excluded
UPDATE ev_log
SET include_in_statistics = false
WHERE data_source = 'TESLA_IMPORT';

-- Mark seed data as excluded
UPDATE ev_log
SET include_in_statistics = false
WHERE car_id IN (
    SELECT c.id
    FROM car c
    JOIN app_user u ON u.id = c.user_id
    WHERE u.is_seed_data = true
);

-- Verify the changes
SELECT
    include_in_statistics,
    COUNT(*) as log_count,
    COUNT(DISTINCT car_id) as car_count
FROM ev_log
GROUP BY include_in_statistics;

-- Show which models have real community data now
SELECT
    c.model,
    COUNT(*) as total_logs,
    SUM(CASE WHEN l.include_in_statistics THEN 1 ELSE 0 END) as included_logs,
    COUNT(DISTINCT CASE WHEN l.include_in_statistics THEN c.user_id END) as unique_contributors
FROM ev_log l
JOIN car c ON c.id = l.car_id
GROUP BY c.model
HAVING SUM(CASE WHEN l.include_in_statistics THEN 1 ELSE 0 END) > 0
ORDER BY c.model;
