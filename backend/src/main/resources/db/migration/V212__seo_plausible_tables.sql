-- Admin-Tab "SEO & Traffic", Phase 3: Plausible (Verhalten nach dem Klick) nächtlich in die eigene DB.
-- Rein additiv (Expand). Ein Fenster wird beim Sync komplett ersetzt. Pfade normalisiert wie gsc_page_daily.
-- bounce_rate in Prozent (0-100, wie Plausible), visit_duration in Sekunden.

CREATE TABLE plausible_day (
    stat_date      DATE          PRIMARY KEY,
    visitors       INTEGER       NOT NULL DEFAULT 0,
    visits         INTEGER       NOT NULL DEFAULT 0,
    pageviews      INTEGER       NOT NULL DEFAULT 0,
    bounce_rate    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    visit_duration INTEGER       NOT NULL DEFAULT 0
);

CREATE TABLE plausible_page_daily (
    stat_date      DATE          NOT NULL,
    path           VARCHAR(500)  NOT NULL,
    visitors       INTEGER       NOT NULL DEFAULT 0,
    pageviews      INTEGER       NOT NULL DEFAULT 0,
    entries        INTEGER       NOT NULL DEFAULT 0,
    bounce_rate    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    visit_duration INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, path)
);
CREATE INDEX idx_plausible_page_daily_path ON plausible_page_daily(path);

-- Goal-Besucher je Einstiegsseite; Goals als Zeilen, Liste ist Konfiguration (seo.plausible.goals).
CREATE TABLE plausible_page_goal_daily (
    stat_date   DATE          NOT NULL,
    path        VARCHAR(500)  NOT NULL,
    goal        VARCHAR(100)  NOT NULL,
    visitors    INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, path, goal)
);

-- Quelle x Medium x Kampagne; Plausible-"(not set)" wird als leerer String gespeichert.
CREATE TABLE plausible_source_daily (
    stat_date    DATE          NOT NULL,
    source       VARCHAR(200)  NOT NULL,
    utm_medium   VARCHAR(200)  NOT NULL DEFAULT '',
    utm_campaign VARCHAR(200)  NOT NULL DEFAULT '',
    visitors     INTEGER       NOT NULL DEFAULT 0,
    visits       INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, source, utm_medium, utm_campaign)
);

CREATE TABLE plausible_source_goal_daily (
    stat_date    DATE          NOT NULL,
    source       VARCHAR(200)  NOT NULL,
    utm_medium   VARCHAR(200)  NOT NULL DEFAULT '',
    utm_campaign VARCHAR(200)  NOT NULL DEFAULT '',
    goal         VARCHAR(100)  NOT NULL,
    visitors     INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date, source, utm_medium, utm_campaign, goal)
);
