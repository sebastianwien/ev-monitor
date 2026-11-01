-- Tesla-Ersatzfahrten (TESLA_INFERRED) entfallen. Sie sahen aus wie echte Fahrten und trugen einen SoC,
-- der über Tage Fahren, Standverlust und Laden mischte. Die Lücke im Kilometerstand zeigt das Logbuch
-- jetzt beim Lesen als Strecke ohne Aufzeichnung. Soft-Delete wie beim Löschen durch den Nutzer: eine
-- wiederhergestellte Fahrt füllt die Lücke wieder, der Sync legt gelöschte Fahrten nicht neu an.
-- Vom Nutzer zusammengeführte oder bearbeitete Fahrten bleiben: sie tragen seine Arbeit und beim
-- Zusammenführen auch die echte Fahrt, die hineingelaufen ist.
UPDATE ev_trip
SET deleted_at = now()
WHERE data_source = 'TESLA_INFERRED'
  AND deleted_at IS NULL
  AND user_created = FALSE
  AND user_edited_at IS NULL;
