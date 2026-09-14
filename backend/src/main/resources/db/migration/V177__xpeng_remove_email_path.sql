-- XPeng EU-Data-Act Email-Weg (AutoSync) wird ersatzlos entfernt: XPeng bietet keine
-- offizielle Schnittstelle, der Mail-Round-Trip wird nicht mehr supportet. Der manuelle
-- ZIP-Upload (xpeng_import_job) bleibt unberuehrt und laeuft ohne Connection weiter.
--
-- Vor dem Drop: Einwilligungs-Nachweise (Art. 7 DSGVO) in eine schlanke Audit-Tabelle
-- archivieren. Sie bleibt im Loeschscope (Kontoloeschung purged sie ueber user_id) und
-- wird per TTL-Job nach 3 Jahren bereinigt.

CREATE TABLE xpeng_consent_audit (
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL,
    vin                VARCHAR(17),
    consent_version    VARCHAR(16),
    consent_granted_at TIMESTAMP,
    consent_revoked_at TIMESTAMP,
    consent_ip         VARCHAR(45),
    consent_user_agent TEXT,
    archived_at        TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_xpeng_consent_audit_user ON xpeng_consent_audit(user_id);
CREATE INDEX idx_xpeng_consent_audit_archived ON xpeng_consent_audit(archived_at);

COMMENT ON TABLE xpeng_consent_audit IS 'Einwilligungs-Nachweis aus dem entfernten XPeng-Mail-Weg (Art. 7 DSGVO). Im Loeschscope, TTL 3 Jahre.';

INSERT INTO xpeng_consent_audit (id, user_id, vin, consent_version,
       consent_granted_at, consent_revoked_at, consent_ip, consent_user_agent)
SELECT id, user_id, vin, consent_version,
       consent_granted_at, consent_revoked_at, consent_ip, consent_user_agent
FROM xpeng_connection;

-- Reihenfolge: erst die abhaengige Mail-Tabelle, dann die connection_id-Referenz im Job,
-- zuletzt die Connection selbst.
DROP TABLE xpeng_received_mail;
ALTER TABLE xpeng_import_job DROP COLUMN connection_id;
DROP TABLE xpeng_connection;
