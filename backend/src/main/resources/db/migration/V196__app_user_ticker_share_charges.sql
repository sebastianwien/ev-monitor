-- Opt-out: öffentliche Ladungen dürfen ohne Namen, Auto, Ort und Uhrzeit im Ticker erscheinen.
ALTER TABLE app_user ADD COLUMN ticker_share_charges BOOLEAN NOT NULL DEFAULT TRUE;
