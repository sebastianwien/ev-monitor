-- Eingaben des persönlichen Reichweitenrechners (Pendelstrecke, Autobahnanteil, Heimladen, Langstrecke).
-- Füllt den Rechner auf anderen Modellseiten vor. Additiv, nullable.
ALTER TABLE app_user ADD COLUMN range_profile JSONB;
