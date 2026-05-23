-- XPeng AutoSync Phase 2: automatischer E-Mail-Versand und IMAP-Empfang im Namen des Users

ALTER TABLE xpeng_connection
    ADD COLUMN routing_token     UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN auto_sync_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN xpeng_email       VARCHAR(255);

CREATE UNIQUE INDEX uq_xpeng_connection_routing_token
    ON xpeng_connection(routing_token);

COMMENT ON COLUMN xpeng_connection.routing_token     IS 'Stabiler Token, der als [token:<uuid>] im E-Mail-Subject eingebettet wird. Ermoeglicht IMAP-Routing ohne Plus-Addressing.';
COMMENT ON COLUMN xpeng_connection.auto_sync_enabled IS 'Wenn true, schickt der Scheduler alle 14 Tage automatisch eine DA-Anfrage an XPeng.';
COMMENT ON COLUMN xpeng_connection.xpeng_email       IS 'Optionale E-Mail des Users bei XPeng fuer CC. Fallback: App-User-E-Mail.';

CREATE TABLE xpeng_received_mail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id   UUID        NOT NULL REFERENCES xpeng_connection(id) ON DELETE CASCADE,
    message_id      TEXT        NOT NULL,
    received_at     TIMESTAMP   NOT NULL,
    attachment_name TEXT,
    job_id          UUID        REFERENCES xpeng_import_job(id) ON DELETE SET NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_xpeng_received_mail_msgid UNIQUE (message_id)
);

CREATE INDEX idx_xpeng_received_mail_connection ON xpeng_received_mail(connection_id);

COMMENT ON TABLE  xpeng_received_mail            IS 'Audit-Log fuer per IMAP empfangene XPeng-Antwortmails. XLSX-Datei selbst wird nicht gespeichert.';
COMMENT ON COLUMN xpeng_received_mail.message_id IS 'Message-ID-Header der empfangenen Mail. Verhindert Doppelverarbeitung.';
