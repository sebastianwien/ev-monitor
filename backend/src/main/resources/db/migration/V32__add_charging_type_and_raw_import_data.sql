-- Add charging type (AC/DC/UNKNOWN) and raw import data (JSONB) to ev_log
ALTER TABLE ev_log ADD COLUMN charging_type VARCHAR(10) NULL;
ALTER TABLE ev_log ADD COLUMN raw_import_data JSONB NULL;
