-- Herkunft des Streckentyps: USER (Formular), SOURCE (von der Quelle geliefert), TRIPS (aus Fahrten),
-- HEURISTIC (Faustregel). Expand-only, alter Code ignoriert die Spalte.
ALTER TABLE ev_log ADD COLUMN IF NOT EXISTS route_type_source VARCHAR(20);

-- Bestand: HIGHWAY/CITY aus dem Formular waren bewusst gewählt. COMBINED war vorausgewählt und bleibt ohne Herkunft.
UPDATE ev_log SET route_type_source = 'USER'
WHERE route_type IN ('HIGHWAY', 'CITY') AND data_source = 'USER_LOGGED' AND route_type_source IS NULL;

-- Tessie hat den Streckentyp aus den eigenen Fahrten abgeleitet.
UPDATE ev_log SET route_type_source = 'TRIPS'
WHERE route_type IS NOT NULL AND data_source = 'TESSIE' AND route_type_source IS NULL;

-- Spritmonitor liefert ihn aus den Notiz-Wörtern der Tankung.
UPDATE ev_log SET route_type_source = 'SOURCE'
WHERE route_type IS NOT NULL AND data_source = 'SPRITMONITOR_IMPORT' AND route_type_source IS NULL;
