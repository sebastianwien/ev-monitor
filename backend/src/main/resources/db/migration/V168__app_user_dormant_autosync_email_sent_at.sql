-- V168: "Bereits versendet"-Marker fuer die Dormant-AutoSync-Mail.
--
-- Ersetzt den exact-day-match (last_seen::date = heute-21) durch last_seen::date <= heute-21
-- AND dormant_autosync_email_sent_at IS NULL. Damit holt der taegliche Scheduler-Lauf automatisch
-- auch den kompletten Bestand an bereits laenger als 21 Tage abwesenden Usern nach, statt sie zu
-- verpassen, weil ihr last_seen-Datum den exakten Tages-Treffer schon ueberschritten hat - kein
-- separater Backfill-Job noetig. Nullable, wird beim Versand gesetzt und danach nie wieder.
ALTER TABLE app_user ADD COLUMN dormant_autosync_email_sent_at TIMESTAMP NULL;
