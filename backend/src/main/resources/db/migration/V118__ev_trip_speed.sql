-- Aggregierte Geschwindigkeit pro Trip. Hersteller-unabhaengig: XPeng aggregiert
-- aus per-sample esp_vehspd, Tesla-Telemetry aus VehicleSpeed (sobald abonniert),
-- Tessie/Tronity-Imports aus avg_speed/max_speed pro Drive-Record.
-- NULL fuer Quellen ohne Speed-Daten (Smartcar, USER_CREATED, manuelle Imports).
ALTER TABLE ev_trip
    ADD COLUMN avg_speed_kmh NUMERIC(5,2),
    ADD COLUMN max_speed_kmh NUMERIC(5,2),
    ADD CONSTRAINT ev_trip_speed_range CHECK (
        (avg_speed_kmh IS NULL OR avg_speed_kmh BETWEEN 0 AND 300) AND
        (max_speed_kmh IS NULL OR max_speed_kmh BETWEEN 0 AND 300) AND
        (avg_speed_kmh IS NULL OR max_speed_kmh IS NULL OR avg_speed_kmh <= max_speed_kmh)
    );
