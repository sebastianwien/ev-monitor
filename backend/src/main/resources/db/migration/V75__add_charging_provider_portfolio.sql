-- Multi-Tarif-Portfolio: user_charging_providers wird von "ein aktiver Tarif" zu "beliebig viele".
-- Neues label-Feld für user-definierte Namen (z.B. "Arbeit RFID", "Meine EnBW Karte").
-- ev_log bekommt FK auf den genutzten Tarif für DB-seitige Auswertung.

ALTER TABLE user_charging_providers
    ADD COLUMN label VARCHAR(100) NULL;

ALTER TABLE ev_log
    ADD COLUMN charging_provider_id UUID NULL
        REFERENCES user_charging_providers(id) ON DELETE SET NULL;

CREATE INDEX idx_ev_log_charging_provider ON ev_log(charging_provider_id)
    WHERE charging_provider_id IS NOT NULL;
