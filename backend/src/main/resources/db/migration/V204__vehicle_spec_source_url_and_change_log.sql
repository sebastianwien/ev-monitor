-- Admin-Tab Fahrzeug-Specs, Release A1. Rein additiv (Expand).
-- source_url: Beleg je Zeile (ev-database o. ä.), Pflicht für neue Zeilen im Tab, NOT NULL erst,
-- wenn die Arbeitsliste "ohne Quelle" leer ist.
-- vehicle_specification_change: Änderungsprotokoll jeder Admin-Aktion. spec_id ohne FK, damit
-- das Protokoll einer gelöschten Zeile erhalten bleibt.

ALTER TABLE vehicle_specification
    ADD COLUMN IF NOT EXISTS source_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS vehicle_specification_change (
    id            UUID PRIMARY KEY,
    spec_id       UUID         NOT NULL,
    admin_user_id UUID         NOT NULL REFERENCES app_user(id),
    changed_at    TIMESTAMP    NOT NULL,
    action        VARCHAR(20)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'REASSIGN_CAR')),
    field         VARCHAR(60),
    old_value     TEXT,
    new_value     TEXT,
    warning       TEXT
);

CREATE INDEX IF NOT EXISTS idx_vehicle_specification_change_spec
    ON vehicle_specification_change (spec_id, changed_at DESC);
