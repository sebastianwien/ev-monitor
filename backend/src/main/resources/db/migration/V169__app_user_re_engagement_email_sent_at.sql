-- V169: "Bereits versendet"-Marker fuer die Re-Engagement-Mail (last_log-basiert).
--
-- Gleicher Umbau wie V168 fuer die Dormant-AutoSync-Mail: ersetzt den exact-day-match
-- (last_log = heute-28) durch last_log <= heute-28 AND re_engagement_email_sent_at IS NULL.
-- Ohne das verpasst jeder User, dessen Schwellenwert-Tag vor Einfuehrung dieses Features
-- oder an einem Tag mit Deploy-Downtime lag, die Mail fuer immer - 161 User aktuell betroffen
-- (Stand 01.09.2026), 5 davon nachweislich seit Feature-Launch (21.03.2026) nie erreichbar.
ALTER TABLE app_user ADD COLUMN re_engagement_email_sent_at TIMESTAMP NULL;
