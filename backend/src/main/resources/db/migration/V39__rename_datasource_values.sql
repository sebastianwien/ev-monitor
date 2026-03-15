-- Rename DataSource enum values to be more descriptive
UPDATE ev_log SET data_source = 'TESLA_LIVE'          WHERE data_source = 'TESLA_HOME';
UPDATE ev_log SET data_source = 'TESLA_FLEET_IMPORT'  WHERE data_source = 'TESLA_FLEET';
UPDATE ev_log SET data_source = 'TESLA_MANUAL_IMPORT' WHERE data_source = 'TESLA_LOGGER_IMPORT';
-- TESLA_IMPORT bleibt (historische Owner API Daten, kein aktives Polling mehr)
