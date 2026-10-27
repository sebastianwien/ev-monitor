-- Vorschau der neuen App-Oberfläche (vorschau.ev-monitor.net) für eingeladene Fahrer.
-- Eigenes Merkmal statt der Rolle BETA_TESTER (die schaltet Premium frei). Im Admin setzbar,
-- steht als Claim "preview" im JWT; die Vorschau schickt alle anderen auf die Hauptadresse.
-- Nur Expand: neue Spalte mit Default, alter Code ignoriert sie.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS ui_preview BOOLEAN NOT NULL DEFAULT FALSE;
