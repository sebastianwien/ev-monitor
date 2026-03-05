-- Add Fleet API fields to tesla_connections
-- Legacy Owner API connections have auth_type = 'OWNER_API'
-- New Fleet API connections have auth_type = 'FLEET_API' and store refresh_token + vin

ALTER TABLE tesla_connections
    ADD COLUMN auth_type VARCHAR(20) NOT NULL DEFAULT 'OWNER_API',
    ADD COLUMN refresh_token TEXT,
    ADD COLUMN vin VARCHAR(20),
    ADD COLUMN last_history_sync_at TIMESTAMP;

COMMENT ON COLUMN tesla_connections.auth_type IS 'OWNER_API (legacy) or FLEET_API (OAuth2)';
COMMENT ON COLUMN tesla_connections.refresh_token IS 'AES-encrypted Fleet API refresh token (long-lived)';
COMMENT ON COLUMN tesla_connections.vin IS 'Vehicle VIN for Fleet API calls';
COMMENT ON COLUMN tesla_connections.last_history_sync_at IS 'Last time charging_history was polled';
