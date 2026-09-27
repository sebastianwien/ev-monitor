-- AutoSync-Slots: ein AutoSync-Abo deckt ein Fahrzeug ab. Anzahl aktiver AutoSync-Abos je Nutzer,
-- gepflegt vom Stripe-Webhook. Standard 1 = heutiges Verhalten (eine Smartcar-Verbindung).
ALTER TABLE app_user ADD COLUMN autosync_slots INTEGER NOT NULL DEFAULT 1;
