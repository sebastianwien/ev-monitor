-- go-eCharger Cloud API integration
-- Polling-based: our backend polls https://{serial}.api.v3.go-e.io/api/status every 30s
-- Session detection via car state transitions (1=Idle, 2=Charging, 3=WaitCar, 4=Complete)
CREATE TABLE goe_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    car_id UUID NOT NULL,
    serial VARCHAR(64) NOT NULL,
    api_key TEXT NOT NULL,
    display_name VARCHAR(255),
    car_state INTEGER NOT NULL DEFAULT 1,
    session_started_at TIMESTAMP,
    last_polled_at TIMESTAMP,
    last_poll_error VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_goe_connections_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_goe_connections_car
        FOREIGN KEY (car_id) REFERENCES cars(id) ON DELETE CASCADE,
    CONSTRAINT uq_goe_connections_serial UNIQUE (serial)
);

CREATE INDEX idx_goe_connections_user_id ON goe_connections(user_id);
CREATE INDEX idx_goe_connections_active ON goe_connections(active) WHERE active = true;

COMMENT ON TABLE goe_connections IS 'go-eCharger Cloud API connections, polled every 30s';
COMMENT ON COLUMN goe_connections.api_key IS 'AES-encrypted go-e Cloud API key';
COMMENT ON COLUMN goe_connections.car_state IS '1=Idle 2=Charging 3=WaitCar 4=Complete 5=Error';
COMMENT ON COLUMN goe_connections.session_started_at IS 'Timestamp when car_state→2, for duration calculation';
