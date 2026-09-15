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
    max_ac_kw      NUMERIC(6,1) NULL,             -- hoechste AC-Steckerleistung
    max_dc_kw      NUMERIC(6,1) NULL,             -- hoechste DC-Steckerleistung, NULL = kein DC
    charge_points  INTEGER      NOT NULL DEFAULT 1,
    source         VARCHAR(20)  NOT NULL DEFAULT 'REGISTER',
    -- Registerdaten der naechstgelegenen Ladeeinrichtung des Betreibers in der Zelle
    register_id    INTEGER      NULL,             -- Ladeeinrichtungs_ID der Bundesnetzagentur
    street         VARCHAR(150) NULL,
    house_number   VARCHAR(20)  NULL,
    postal_code    VARCHAR(10)  NULL,
    city           VARCHAR(100) NULL,
    plug_types     VARCHAR(100) NULL,             -- "Typ 2, CCS, CHAdeMO"
    commissioned_on DATE        NULL,
    site_label     VARCHAR(150) NULL,             -- Standortbezeichnung des Betreibers
    payment        VARCHAR(200) NULL,
    opening_hours  VARCHAR(200) NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_charging_site_cell_name UNIQUE (geohash, name_key)
);

ALTER TABLE ev_log ADD COLUMN charging_site_id UUID NULL REFERENCES charging_site(id);
CREATE INDEX idx_ev_log_charging_site ON ev_log(charging_site_id) WHERE charging_site_id IS NOT NULL;
