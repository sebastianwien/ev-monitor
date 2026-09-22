-- Oeffentlich teilbare Fahrzeugseite (Verbrauch, Ladekosten, Ladeliste).
--
-- Opt-in pro Fahrzeug, gleiches Muster wie share_token auf ev_log (V151):
-- der Token entsteht erst beim Teilen, Widerruf setzt ihn auf NULL, ein
-- erneutes Teilen vergibt einen neuen. Die URL verraet keine interne ID.
ALTER TABLE car ADD COLUMN share_token VARCHAR(16);
ALTER TABLE car ADD COLUMN share_created_at TIMESTAMP;

CREATE UNIQUE INDEX ux_car_share_token
    ON car (share_token)
    WHERE share_token IS NOT NULL;
