-- Tarif-Crawler: Quellen je Anbieter, Vorschläge aus den Läufen, und drei Tarif-Felder, die die
-- geprüften Seiten brauchen (Stand 27.10.2026). Nur Erweiterungen mit Default (Expand): alter Code
-- liest und schreibt die Tabelle weiter, die neuen Spalten bleiben für ihn unsichtbar.

-- Gilt an allen Betreibern, für die der Anbieter keinen genaueren Preis hat (Maingau "Standardpreis:
-- alle anderen Ladepunkte", ADAC "andere Betreiber 0,75"). Genauer sind Betreiber, Tier und eigenes Netz.
ALTER TABLE charging_provider_tariffs
    ADD COLUMN applies_elsewhere BOOLEAN NOT NULL DEFAULT FALSE;

-- Untere Leistungsgrenze in kW, ausschließlich: die Zeile gilt für Ladungen mit mehr Leistung
-- (Aral "DC über 50 kW", ÖAMTC "ab 150 kW"). NULL = ab 0 kW.
ALTER TABLE charging_provider_tariffs
    ADD COLUMN min_power_kw INTEGER NULL CHECK (min_power_kw IS NULL OR min_power_kw BETWEEN 1 AND 1000);

-- Höchstbetrag der Blockiergebühr je Ladung (EnBW und Maingau: 12 €). NULL = kein Deckel bekannt.
ALTER TABLE charging_provider_tariffs
    ADD COLUMN blocking_fee_cap_eur NUMERIC(6, 2) NULL CHECK (blocking_fee_cap_eur IS NULL OR blocking_fee_cap_eur >= 0);

-- Eine Seite, aus der der Crawler die Tarife eines Anbieters liest.
-- mode EXTRACT: Text lesen und Preise vergleichen. WATCH: Preise stehen nicht als Text auf der Seite
-- (EnBW: Bilder), der Crawler merkt sich nur einen Fingerabdruck und meldet, wenn er sich ändert.
CREATE TABLE tariff_source (
    id                   UUID PRIMARY KEY,
    emp_name             VARCHAR(100) NOT NULL REFERENCES emp(name) ON UPDATE CASCADE,
    url                  VARCHAR(500) NOT NULL UNIQUE,
    mode                 VARCHAR(10)  NOT NULL DEFAULT 'EXTRACT' CHECK (mode IN ('EXTRACT', 'WATCH')),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fetched_at      TIMESTAMP    NULL,
    last_success_at      TIMESTAMP    NULL,
    -- OK, UNCHANGED, CHANGED, HTTP_ERROR, ROBOTS, EMPTY, UNREADABLE, EXTRACT_ERROR
    last_status          VARCHAR(20)  NULL,
    last_http_status     INTEGER      NULL,
    last_error           VARCHAR(300) NULL,
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    -- SHA-256 über den gelesenen Text plus Bild-Alttexte; gleich = Seite unverändert.
    content_hash         VARCHAR(64)  NULL,
    -- Fingerabdruck, den ein Admin zuletzt als geprüft bestätigt hat (nur WATCH).
    approved_hash        VARCHAR(64)  NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Ein Unterschied zwischen Seite und Tabelle. CHANGE: bekannter Tarif, anderer Wert. NEW: Tarif steht
-- auf der Seite, nicht in der Tabelle. MISSING: Tarif in der Tabelle, nicht mehr auf der Seite.
-- WATCH_CHANGED: Fingerabdruck einer WATCH-Quelle geändert. MAPPING: Betreiberliste einer Preisstufe
-- (cpo_emp_tier_mapping) weicht von der Seite ab; nur mit Admin.
-- Ein CHANGE wird ohne Admin übernommen, wenn zwei Läufe denselben Wert liefern und er plausibel ist.
CREATE TABLE tariff_proposal (
    id            UUID PRIMARY KEY,
    source_id     UUID         NOT NULL REFERENCES tariff_source(id) ON DELETE CASCADE,
    emp_name      VARCHAR(100) NOT NULL,
    kind          VARCHAR(20)  NOT NULL CHECK (kind IN ('CHANGE', 'NEW', 'MISSING', 'WATCH_CHANGED', 'MAPPING')),
    tariff_id     UUID         NULL REFERENCES charging_provider_tariffs(id) ON DELETE SET NULL,
    -- Der Tarif, wie er auf der Seite steht (Felder wie im Admin-Formular).
    proposed      JSONB        NOT NULL,
    -- Gleicher Fingerabdruck = derselbe Vorschlag aus einem späteren Lauf.
    fingerprint   VARCHAR(64)  NOT NULL,
    -- Wörtliches Zitat der Seite, aus dem der Wert stammt; vom Crawler gegen den Seitentext geprüft.
    evidence      TEXT         NULL,
    -- Warum der Vorschlag nicht automatisch übernommen wurde.
    hold_reason   VARCHAR(200) NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'SUPERSEDED')),
    seen_count    INTEGER      NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMP    NOT NULL,
    last_seen_at  TIMESTAMP    NOT NULL,
    decided_at    TIMESTAMP    NULL,
    -- AUTO oder ADMIN
    decided_by    VARCHAR(10)  NULL
);

CREATE UNIQUE INDEX uq_tariff_proposal_pending ON tariff_proposal(source_id, fingerprint) WHERE status = 'PENDING';
CREATE INDEX idx_tariff_proposal_status ON tariff_proposal(status);
