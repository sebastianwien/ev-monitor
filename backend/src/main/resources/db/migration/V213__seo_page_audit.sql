-- Admin-Tab "SEO & Traffic", Phase 5: nächtlicher On-Page-Check aller Sitemap-URLs (Unter-Tab "Technik").
-- Rein additiv (Expand). Ein Prüftag wird beim Lauf komplett ersetzt. Pfade normalisiert wie gsc_page_daily.
-- findings: JSON-Liste [{check, severity, message}] als Text, gelesen nur je Prüftag, nie gefiltert in SQL.

CREATE TABLE seo_page_audit (
    stat_date      DATE          NOT NULL,
    path           VARCHAR(500)  NOT NULL,
    http_status    INTEGER       NOT NULL DEFAULT 0,
    score          INTEGER       NOT NULL DEFAULT 0,
    error_count    INTEGER       NOT NULL DEFAULT 0,
    warning_count  INTEGER       NOT NULL DEFAULT 0,
    findings       TEXT          NOT NULL DEFAULT '[]',
    PRIMARY KEY (stat_date, path)
);

-- Core Web Vitals (PageSpeed Insights, mobil) wöchentlich für die meistgesehenen Seiten.
-- source: FIELD (Felddaten echter Chrome-Nutzer) oder LAB (Lighthouse-Messung, ohne INP).
CREATE TABLE seo_page_vitals (
    week_start     DATE          NOT NULL,
    path           VARCHAR(500)  NOT NULL,
    lcp_ms         INTEGER,
    cls            NUMERIC(6,3),
    inp_ms         INTEGER,
    source         VARCHAR(10)   NOT NULL,
    PRIMARY KEY (week_start, path)
);
