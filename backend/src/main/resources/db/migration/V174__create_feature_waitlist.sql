-- Per-User Opt-in auf Feature-Wartelisten ("benachrichtige mich, sobald verfuegbar").
-- Aktuell genutzt fuer XPENG_AUTOSYNC (automatischer EU-Data-Act-Import), generisch fuer weitere Features.
CREATE TABLE feature_waitlist (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    feature    VARCHAR(64) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_feature_waitlist_user_feature UNIQUE (user_id, feature)
);

-- Marketing/Admin: schnelle Zaehlung des Interesses je Feature.
CREATE INDEX idx_feature_waitlist_feature ON feature_waitlist (feature);
