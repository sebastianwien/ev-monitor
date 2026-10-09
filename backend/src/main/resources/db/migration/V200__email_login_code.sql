-- Einmal-Codes für den Login per Mail (Gate auf der Modellseite). Nur der Hash wird gespeichert,
-- Zeilen leben höchstens 10 Minuten und werden bei Erfolg, neuem Code oder Ablauf gelöscht. Keine IP.
CREATE TABLE email_login_code (
    id          UUID PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL
);
CREATE INDEX idx_email_login_code_email ON email_login_code (email);
CREATE INDEX idx_email_login_code_expires_at ON email_login_code (expires_at);
