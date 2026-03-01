-- Add flag to control which logs are included in statistics/public data
-- Clean approach: single source of truth instead of string matching on data_source

ALTER TABLE ev_log
    ADD COLUMN include_in_statistics BOOLEAN NOT NULL DEFAULT true;

-- Mark Tesla imports as excluded (incomplete data: no cost, no duration)
UPDATE ev_log
SET include_in_statistics = false
WHERE data_source = 'TESLA_IMPORT';

-- Mark seed data as excluded (test data, not real community contributions)
UPDATE ev_log
SET include_in_statistics = false
WHERE car_id IN (
    SELECT c.id
    FROM car c
    JOIN app_user u ON u.id = c.user_id
    WHERE u.is_seed_data = true
);

-- Add index for performance (statistics queries will filter on this)
CREATE INDEX idx_ev_log_include_in_statistics ON ev_log(include_in_statistics);

-- Add comment for clarity
COMMENT ON COLUMN ev_log.include_in_statistics IS 'Whether this log should be included in statistics and public aggregations. False for seed data, incomplete imports, or test data.';
