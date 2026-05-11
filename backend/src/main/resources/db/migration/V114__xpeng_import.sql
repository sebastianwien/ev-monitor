-- XPeng EU-Data-Act Excel-Import: Consent + Job-Lifecycle

CREATE TABLE xpeng_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    car_id UUID NOT NULL,
    vin VARCHAR(17) NOT NULL,

    consent_granted_at TIMESTAMP NOT NULL,
    consent_revoked_at TIMESTAMP,
    consent_ip VARCHAR(45),
    consent_user_agent TEXT,
    consent_version VARCHAR(10) NOT NULL,

    last_request_sent_at TIMESTAMP,
    last_successful_import_at TIMESTAMP,
    total_imports_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_xpeng_connection_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_xpeng_connection_car  FOREIGN KEY (car_id)  REFERENCES car(id)      ON DELETE CASCADE,
    CONSTRAINT uq_xpeng_connection_car  UNIQUE (car_id)
);

CREATE INDEX idx_xpeng_connection_user ON xpeng_connection(user_id) WHERE consent_revoked_at IS NULL;

CREATE TABLE xpeng_import_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    car_id UUID NOT NULL,
    connection_id UUID,

    status VARCHAR(20) NOT NULL,
    file_hash VARCHAR(64),
    file_size_bytes BIGINT,

    data_range_start TIMESTAMP,
    data_range_end TIMESTAMP,

    imported_trips INTEGER NOT NULL DEFAULT 0,
    imported_sessions INTEGER NOT NULL DEFAULT 0,
    skipped_duplicates INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT fk_xpeng_job_user       FOREIGN KEY (user_id)       REFERENCES app_user(id)         ON DELETE CASCADE,
    CONSTRAINT fk_xpeng_job_car        FOREIGN KEY (car_id)        REFERENCES car(id)              ON DELETE CASCADE,
    CONSTRAINT fk_xpeng_job_connection FOREIGN KEY (connection_id) REFERENCES xpeng_connection(id) ON DELETE SET NULL,
    CONSTRAINT chk_xpeng_job_status    CHECK (status IN ('QUEUED','PROCESSING','DONE','FAILED'))
);

CREATE INDEX idx_xpeng_job_user_created ON xpeng_import_job(user_id, created_at DESC);
CREATE INDEX idx_xpeng_job_status ON xpeng_import_job(status) WHERE status IN ('QUEUED','PROCESSING');
-- Same file twice per user: skip silently with friendly error.
CREATE UNIQUE INDEX uq_xpeng_job_user_filehash ON xpeng_import_job(user_id, file_hash) WHERE file_hash IS NOT NULL;

COMMENT ON TABLE xpeng_connection IS 'User consent to request and process XPeng EU-Data-Act telematics data on their behalf.';
COMMENT ON TABLE xpeng_import_job IS 'XPeng XLSX import job lifecycle. Excel file itself is never persisted - tempfile only during processing.';
COMMENT ON COLUMN xpeng_connection.consent_version IS 'Version of the consent text the user agreed to. Bump when wording changes to trigger re-consent.';
