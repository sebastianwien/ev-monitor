-- Ladestandort als eigene Entitaet (DEV-7). Entsteht nur aus dem Ladesaeulenregister der
-- Bundesnetzagentur, wenn ein Nutzer dort eine Saeule fuer sein Log auswaehlt.
-- Bewusst ohne user_id: ein oeffentlicher Standort ist kein Nutzerdatum, die Beziehung
-- entsteht ueber ev_log.charging_site_id. Zelle mit 7 Stellen (~150 m) wie bei
-- oeffentlichen Ladungen ueblich - nie eine exakte Position.

CREATE TABLE charging_site (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    name_key       VARCHAR(100) NOT NULL,          -- lower(trim(name)) fuer den Abgleich
    cpo_name       VARCHAR(100) NULL REFERENCES charging_networks(name) ON UPDATE CASCADE,
    geohash        VARCHAR(7)   NOT NULL,
    max_power_kw   NUMERIC(6,1) NULL,
    charge_points  INTEGER      NOT NULL DEFAULT 1,
    fast_charging  BOOLEAN      NOT NULL DEFAULT false,
    source         VARCHAR(20)  NOT NULL DEFAULT 'REGISTER',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_charging_site_cell_name UNIQUE (geohash, name_key)
);

ALTER TABLE ev_log ADD COLUMN charging_site_id UUID NULL REFERENCES charging_site(id);
CREATE INDEX idx_ev_log_charging_site ON ev_log(charging_site_id) WHERE charging_site_id IS NOT NULL;
