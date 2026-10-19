-- Admin-Tab "SEO & Traffic", Phase 6: Unter-Tab "Wettbewerb" mit Daten von DataForSEO.
-- Rein additiv (Expand). Eine Woche bzw. ein Monat wird beim Abruf komplett ersetzt.

-- Top-10 der Google-Suche (Deutschland, mobil) je Suchbegriff und Woche (Montag).
CREATE TABLE serp_snapshot (
    week_start  DATE          NOT NULL,
    query       VARCHAR(500)  NOT NULL,
    serp_rank   INTEGER       NOT NULL,
    domain      VARCHAR(255)  NOT NULL,
    url         VARCHAR(2000) NOT NULL,
    title       VARCHAR(500),
    PRIMARY KEY (week_start, query, serp_rank)
);
CREATE INDEX idx_serp_snapshot_domain ON serp_snapshot(week_start, domain);

-- Suchvolumen und CPC (Google Ads, Deutschland) je Begriff und Monat (Monatserster). cpc in US-Dollar wie geliefert.
CREATE TABLE keyword_metrics (
    month_start   DATE          NOT NULL,
    query         VARCHAR(500)  NOT NULL,
    search_volume INTEGER,
    cpc           NUMERIC(10,2),
    competition   VARCHAR(10),
    PRIMARY KEY (month_start, query)
);

-- Begriffsideen zu unseren wichtigsten Suchbegriffen je Monat.
CREATE TABLE keyword_idea (
    month_start   DATE          NOT NULL,
    keyword       VARCHAR(500)  NOT NULL,
    seed          VARCHAR(500)  NOT NULL,
    search_volume INTEGER,
    cpc           NUMERIC(10,2),
    PRIMARY KEY (month_start, keyword)
);

-- Kosten eines Abrufs in US-Dollar (nur bezahlte Quellen), für das Monatsbudget.
ALTER TABLE seo_sync_run ADD COLUMN cost_usd NUMERIC(10,4);
