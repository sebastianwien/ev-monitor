-- Import-Protokoll (Herstellerarchitektur R2b): eine Zeile je Import ueber das IngestGateway plus
-- Parse-Fehler aus den Upload-Pfaden (VW, XPeng). Betriebsprotokoll ohne Inhalte: Quelle, Ergebnis,
-- Mengen, bereinigte Fehlerart, Dauer. Zweck: Stoerungen und Formataenderungen bei Herstellern
-- erkennen (Admin-Tab "Importe"). Kontoloeschung raeumt per FK mit, TTL-Job nach 90 Tagen.

CREATE TABLE import_event (
    id                UUID PRIMARY KEY,
    user_id           UUID        REFERENCES app_user(id) ON DELETE CASCADE,
    car_id            UUID        REFERENCES car(id) ON DELETE SET NULL,
    provider          VARCHAR(20) NOT NULL,
    channel           VARCHAR(10) NOT NULL,
    data_source       VARCHAR(40) NOT NULL,
    outcome           VARCHAR(20) NOT NULL,
    sessions_imported INTEGER     NOT NULL DEFAULT 0,
    sessions_skipped  INTEGER     NOT NULL DEFAULT 0,
    sessions_failed   INTEGER     NOT NULL DEFAULT 0,
    trips_imported    INTEGER     NOT NULL DEFAULT 0,
    trips_skipped     INTEGER     NOT NULL DEFAULT 0,
    error             VARCHAR(200),
    duration_ms       INTEGER,
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_event_created ON import_event(created_at);
CREATE INDEX idx_import_event_user ON import_event(user_id);

COMMENT ON TABLE import_event IS 'Import-Protokoll je Gateway-Aufruf und Parse-Fehler, ohne Inhalte. Im Loeschscope, TTL 90 Tage.';
