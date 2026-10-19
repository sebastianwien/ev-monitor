-- Admin-Tab "SEO & Traffic", Phase 2: First-Touch-Attribution. Erste besuchte Seite (nur Pfad, ohne
-- Query) je Konto, damit Registrierungen einer Seite zugeordnet werden können. Rein additiv.
ALTER TABLE app_user ADD COLUMN landing_path VARCHAR(200);
COMMENT ON COLUMN app_user.landing_path IS 'Erste besuchte Seite vor der Registrierung (Pfad ohne Query), normalisiert wie gsc_page_daily.path';
CREATE INDEX idx_app_user_landing_path ON app_user(landing_path) WHERE landing_path IS NOT NULL;
