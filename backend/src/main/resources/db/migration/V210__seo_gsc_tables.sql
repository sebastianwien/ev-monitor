-- Admin-Tab "SEO & Traffic", Phase 1: Google Search Console nächtlich in die eigene DB.
-- Rein additiv (Expand). Ein Tag wird beim Sync immer komplett ersetzt (GSC reift 2-3 Tage nach),
-- deshalb keine Upsert-Spalten. CTR wird nicht gespeichert (= clicks / impressions).

CREATE TABLE gsc_page_daily (
    stat_date   DATE          NOT NULL,
    path        VARCHAR(500)  NOT NULL,
    clicks      INTEGER       NOT NULL DEFAULT 0,
    impressions INTEGER       NOT NULL DEFAULT 0,
    avg_position NUMERIC(6,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, path)
);
CREATE INDEX idx_gsc_page_daily_path ON gsc_page_daily(path);

CREATE TABLE gsc_query_daily (
    stat_date   DATE          NOT NULL,
    query       VARCHAR(500)  NOT NULL,
    path        VARCHAR(500)  NOT NULL,
    clicks      INTEGER       NOT NULL DEFAULT 0,
    impressions INTEGER       NOT NULL DEFAULT 0,
    avg_position NUMERIC(6,2) NOT NULL DEFAULT 0,
    is_brand    BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (stat_date, query, path)
);
CREATE INDEX idx_gsc_query_daily_date ON gsc_query_daily(stat_date);

CREATE TABLE gsc_segment_daily (
    stat_date   DATE          NOT NULL,
    path        VARCHAR(500)  NOT NULL,
    country     VARCHAR(3)    NOT NULL,
    device      VARCHAR(10)   NOT NULL,
    clicks      INTEGER       NOT NULL DEFAULT 0,
    impressions INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, path, country, device)
);

-- Protokoll je Sync-Lauf für den Tab-Kopf "Letzter Sync".
CREATE TABLE seo_sync_run (
    id          UUID          PRIMARY KEY,
    source      VARCHAR(20)   NOT NULL,
    started_at  TIMESTAMP     NOT NULL,
    finished_at TIMESTAMP,
    status      VARCHAR(10)   NOT NULL,
    row_count   INTEGER       NOT NULL DEFAULT 0,
    from_day    DATE          NOT NULL,
    to_day      DATE          NOT NULL,
    error       TEXT
);
CREATE INDEX idx_seo_sync_run_started ON seo_sync_run(started_at DESC);

-- Mini-Lock für Jobs, die im Blue/Green-Deploy auf beiden Instanzen starten könnten.
-- Lock holen = UPDATE ... WHERE locked_until < now(), genau eine betroffene Zeile. Zeile legt der Job selbst an.
CREATE TABLE job_lock (
    name         VARCHAR(50)  PRIMARY KEY,
    locked_until TIMESTAMP    NOT NULL
);
