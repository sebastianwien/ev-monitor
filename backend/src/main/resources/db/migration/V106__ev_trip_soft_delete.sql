ALTER TABLE ev_trip ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_ev_trip_deleted_at ON ev_trip(deleted_at) WHERE deleted_at IS NOT NULL;
