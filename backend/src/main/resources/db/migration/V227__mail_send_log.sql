-- Versandprotokoll der Nutzer-Mails (Admin-Tab "Mailings"). Kein Inhalt, keine Adresse:
-- nur Mailtyp, Nutzer (fällt mit dem Konto weg) und Zeitpunkt. Einträge älter als 12 Monate löscht ein Job.
CREATE TABLE mail_send_log (
    id        BIGSERIAL    PRIMARY KEY,
    mail_type VARCHAR(60)  NOT NULL,
    user_id   UUID         REFERENCES app_user(id) ON DELETE CASCADE,
    sent      BOOLEAN      NOT NULL,
    sent_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_mail_send_log_type_time ON mail_send_log(mail_type, sent_at DESC);
CREATE INDEX idx_mail_send_log_user ON mail_send_log(user_id);
CREATE INDEX idx_mail_send_log_time ON mail_send_log(sent_at);
