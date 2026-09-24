-- Pseudonymisierte Kopien hochgeladener Herstellerdateien (VW EU-Data-Act-Upload und
-- Historien-Export, XPeng-CSV-Export). Zweck: Formatabweichungen erkennen und die
-- Erkennung mit echten Dateien testen. VIN, Konto-IDs und Standorte sind vor dem Speichern
-- ersetzt bzw. verschoben. Pseudonymisiert, nicht anonym: ueber Zeit und Kilometerstand
-- waere eine Zuordnung zu ev_log moeglich, deshalb FK auf den User (Kontoloeschung
-- raeumt mit) und TTL-Job nach 180 Tagen.

CREATE TABLE import_sample (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    car_model         VARCHAR(100),
    content_sha256    CHAR(64)     NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    content           BYTEA        NOT NULL,
    outcome           VARCHAR(20)  NOT NULL,
    sessions_detected INTEGER,
    trips_detected    INTEGER,
    error             VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_import_sample_provider_sha UNIQUE (provider, content_sha256)
);
CREATE INDEX idx_import_sample_user ON import_sample(user_id);
CREATE INDEX idx_import_sample_created ON import_sample(created_at);

COMMENT ON TABLE import_sample IS 'Pseudonymisierte Kopien von Hersteller-Uploads (VW, XPeng-CSV). Im Loeschscope, TTL 180 Tage.';
