-- Speichert source-spezifische Telemetrie-Aggregate (Batterie-Temp, Driving-Style,
-- Motor-Daten etc.) als generischer jsonb-Blob. Quelle steckt in extras.source,
-- Schema-Version in extras.schema_version. Aktuell nur XPeng-Import befuellt das;
-- spaeter koennen Tessie / XPeng-API / Smartcar denselben Slot mit eigenem Schema
-- nutzen.
ALTER TABLE ev_log  ADD COLUMN telemetry_extras jsonb;
ALTER TABLE ev_trip ADD COLUMN telemetry_extras jsonb;
