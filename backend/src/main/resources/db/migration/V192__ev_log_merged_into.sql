-- Beim Zusammenführen wird die Quelle soft-gelöscht. merged_into unterscheidet sie von einer
-- Nutzer-Löschung: sie gehört nicht in den Papierkorb und darf nicht wiederhergestellt werden,
-- sonst zählt die Ladung doppelt. Kein FK, damit ein Purge des Ziels die Markierung nicht löscht.
ALTER TABLE ev_log ADD COLUMN merged_into UUID;
