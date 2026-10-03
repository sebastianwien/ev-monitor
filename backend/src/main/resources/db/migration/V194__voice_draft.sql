-- Sprachlog: eine Zeile je Sprachaufnahme. Traegt Monatsdeckel (nur success = true zaehlt) und
-- Kosten je Nutzer. Nur Nutzungsmetadaten: kein Audio, kein Transkript, keine Feldwerte.
-- cost_usd wird beim Aufruf aus den Preis-Properties eingefroren. Kontoloeschung raeumt per FK mit.

CREATE TABLE voice_draft (
    id                        UUID PRIMARY KEY,
    user_id                   UUID          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    car_id                    UUID          REFERENCES car(id) ON DELETE SET NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT now(),
    success                   BOOLEAN       NOT NULL,
    error_code                VARCHAR(30),
    audio_seconds             NUMERIC(7,2),
    transcribe_model          VARCHAR(60),
    extract_model             VARCHAR(60),
    transcribe_tokens         INTEGER,
    extract_prompt_tokens     INTEGER,
    extract_completion_tokens INTEGER,
    cost_usd                  NUMERIC(10,6) NOT NULL DEFAULT 0,
    latency_ms                INTEGER,
    fields_filled             INTEGER,
    uncertain_count           INTEGER
);
CREATE INDEX idx_voice_draft_user_created ON voice_draft(user_id, created_at);

COMMENT ON TABLE voice_draft IS 'Sprachlog-Nutzung je Aufnahme ohne Inhalte: Deckel und Kosten je Nutzer. Im Loeschscope.';
