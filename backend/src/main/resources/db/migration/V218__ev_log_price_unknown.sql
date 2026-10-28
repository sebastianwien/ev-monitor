-- "Weiß ich nicht mehr": der Nutzer hat den fehlenden Preis einer Ladung für unbekannt erklärt.
-- Danach fragt keine Stelle mehr danach. Nur sinnvoll bei cost_eur IS NULL, ein späterer Preis
-- setzt das Kennzeichen zurück. Expand-only, alter Code ignoriert die Spalte.
ALTER TABLE ev_log ADD COLUMN IF NOT EXISTS price_unknown BOOLEAN NOT NULL DEFAULT FALSE;
