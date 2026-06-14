-- Veroeffentlichte Web-Bundles fuer Capgo Live-Updates.
-- Die CI legt bei jedem Deploy eine Zeile an; das neueste Bundle (per created_at)
-- wird vom updateUrl-Endpoint /api/app/updates ausgeliefert.
CREATE TABLE app_bundle (
    id         BIGSERIAL PRIMARY KEY,
    version    VARCHAR(32)  NOT NULL,
    checksum   VARCHAR(128) NOT NULL,
    filename   VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_bundle_version UNIQUE (version)
);

CREATE INDEX idx_app_bundle_created_at ON app_bundle (created_at DESC);
