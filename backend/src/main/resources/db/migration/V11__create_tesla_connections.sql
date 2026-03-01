-- Create tesla_connections table for Tesla API integration
CREATE TABLE tesla_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    access_token TEXT NOT NULL,
    vehicle_id VARCHAR(255) NOT NULL,
    vehicle_name VARCHAR(255),
    last_sync_at TIMESTAMP,
    auto_import_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tesla_connections_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_tesla_connections_user
        UNIQUE (user_id)
);

-- Index for user lookups
CREATE INDEX idx_tesla_connections_user_id ON tesla_connections(user_id);

-- Index for auto-import scheduling (for future background jobs)
CREATE INDEX idx_tesla_connections_auto_import ON tesla_connections(auto_import_enabled) WHERE auto_import_enabled = true;

-- Comment
COMMENT ON TABLE tesla_connections IS 'Stores Tesla API access tokens for automatic charging log imports';
COMMENT ON COLUMN tesla_connections.access_token IS 'Encrypted Tesla API access token';
COMMENT ON COLUMN tesla_connections.vehicle_id IS 'Tesla vehicle_id from API';
