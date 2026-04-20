CREATE TABLE smartcar_webhook_raw_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    received_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_id             VARCHAR(36) NOT NULL,
    smartcar_vehicle_id  VARCHAR(36) NOT NULL,
    make                 VARCHAR(64),
    model                VARCHAR(64),
    vehicle_year         INTEGER,
    triggers             JSONB NOT NULL,
    signals              JSONB NOT NULL,
    soc_percent          INTEGER,
    odometer_km          NUMERIC(10,1),
    location_geohash     VARCHAR(8),
    outside_temp_celsius NUMERIC(5,2),
    mode                 VARCHAR(16),
    CONSTRAINT uq_smartcar_webhook_raw_log_event_id UNIQUE (event_id)
);

CREATE INDEX idx_smartcar_webhook_raw_log_vehicle
    ON smartcar_webhook_raw_log (smartcar_vehicle_id, received_at DESC);
