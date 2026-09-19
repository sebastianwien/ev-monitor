-- Soft-Delete für Fahrzeuge: gelöschte Autos bleiben 7 Tage wiederherstellbar,
-- danach räumt der Purge-Job hart ab (FK-Kaskade greift dann wie zuvor).
ALTER TABLE car ADD COLUMN deleted_at TIMESTAMP;

-- Alle Lesepfade filtern auf deleted_at IS NULL.
CREATE INDEX idx_car_user_active ON car(user_id) WHERE deleted_at IS NULL;

-- Der Purge-Job sucht ausschließlich über deleted_at.
CREATE INDEX idx_car_deleted_at ON car(deleted_at) WHERE deleted_at IS NOT NULL;
