-- Offene Frage an den Nutzer, an welcher Register-Säule er geladen hat: bleibt bei einer Ladung
-- mehr als eine Säule im Umkreis offen, wird einmal je Ort und Ladeart gefragt. Die Ladungen
-- einer Frage werden beim Lesen und Beantworten gesucht (gleiche Zelle und Ladeart, ohne Säule).
-- Neue personenbezogene Daten entstehen nicht: Zelle und Ladeart stehen schon an den Ladungen,
-- die Kandidaten sind öffentliche Registerdaten ohne Position.
CREATE TABLE charging_site_question (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    geohash       VARCHAR(7)   NOT NULL,
    -- '' = Ladeart unbekannt, damit der Unique-Schlüssel auch dann greift
    charging_type VARCHAR(10)  NOT NULL,
    candidates    JSONB        NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    answered_at   TIMESTAMP,
    CONSTRAINT uq_charging_site_question UNIQUE (user_id, geohash, charging_type)
);
