-- Inhalts-Hash je geprüfter Seite (Title, Description, Text): erkennt geänderte Seiten für IndexNow.
ALTER TABLE seo_page_audit ADD COLUMN content_hash VARCHAR(64);

-- Index-Status je Sitemap-Pfad, eine Zeile je Pfad mit dem letzten Stand.
-- Google: URL Inspection API (verdict PASS/NEUTRAL/FAIL, Klartext, letzter Crawl, Googles Canonical).
-- IndexNow: Hash und Zeitpunkt der letzten erfolgreichen Meldung an Bing und Co.
CREATE TABLE seo_page_index (
    path              VARCHAR(500)   PRIMARY KEY,
    checked_at        TIMESTAMP,
    verdict           VARCHAR(30),
    coverage_state    VARCHAR(300),
    last_crawl_at     TIMESTAMP,
    google_canonical  VARCHAR(1000),
    indexnow_hash     VARCHAR(64),
    indexnow_at       TIMESTAMP
);

-- Abrufe öffentlicher Seiten durch Such- und KI-Crawler je Tag, Bot und Seitenpfad.
-- Ohne IP und ohne User-Agent-Rohtext: nur der erkannte Bot-Name.
CREATE TABLE seo_bot_daily (
    stat_date  DATE          NOT NULL,
    bot        VARCHAR(30)   NOT NULL,
    path       VARCHAR(500)  NOT NULL,
    hits       INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, bot, path)
);
