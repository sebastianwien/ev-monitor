-- Besonderheiten einer Fahrt, vom Nutzer angegeben (Anhänger, Dachbox, voll beladen).
-- Expand-only: alter Code kennt die Spalte nicht und schreibt über den Default.
ALTER TABLE ev_trip ADD COLUMN load_extras TEXT[] NOT NULL DEFAULT '{}';
