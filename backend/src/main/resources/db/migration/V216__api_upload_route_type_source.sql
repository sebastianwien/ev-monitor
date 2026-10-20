-- Streckentyp aus einem API-Upload kam mit den Nutzerdaten mit, also Herkunft SOURCE (wie bei neuen Uploads seit V215).
UPDATE ev_log SET route_type_source = 'SOURCE'
WHERE route_type IS NOT NULL AND data_source = 'API_UPLOAD' AND route_type_source IS NULL;
