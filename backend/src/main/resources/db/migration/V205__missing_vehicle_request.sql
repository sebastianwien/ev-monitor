-- Meldeweg "Fehlt etwas?" (Release A2). Rein additiv (Expand).
-- Nutzer melden fehlende Varianten, Modelle oder Hersteller statt sie selbst anzulegen.
-- contact_email wird 30 Tage nach done_at genullt (Scheduler), die Meldung bleibt als Protokoll.

CREATE TABLE IF NOT EXISTS missing_vehicle_request (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    contact_email     VARCHAR(254),
    free_text         VARCHAR(150) NOT NULL,
    brand             VARCHAR(60),
    model             VARCHAR(80),
    net_kwh           NUMERIC(6, 2),
    build_year        INTEGER,
    created_at        TIMESTAMP    NOT NULL,
    admin_notified_at TIMESTAMP,
    done_at           TIMESTAMP,
    user_notified_at  TIMESTAMP,
    done_by_admin_id  UUID         REFERENCES app_user(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_missing_vehicle_request_user
    ON missing_vehicle_request (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_missing_vehicle_request_open
    ON missing_vehicle_request (created_at DESC) WHERE done_at IS NULL;
