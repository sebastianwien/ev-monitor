-- Session Grouping für Überschussladen (Solar-Surplus)
-- Mehrere Micro-Sessions derselben Wallbox (WALLBOX_GOE) werden zu einer logischen Gruppe zusammengefasst
-- Merge-Kriterium: Gap < merge_gap_minutes (default 90 min) + gleicher Tag + gleiches Fahrzeug

CREATE TABLE charging_session_group (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id                  UUID NOT NULL REFERENCES car(id) ON DELETE CASCADE,
    total_kwh_charged       NUMERIC(10,2) NOT NULL,
    total_duration_minutes  INTEGER,
    session_start           TIMESTAMP NOT NULL,  -- Beginn der 1. Sub-Session
    session_end             TIMESTAMP NOT NULL,  -- Ende der letzten Sub-Session
    session_count           INTEGER NOT NULL DEFAULT 1,
    geohash                 VARCHAR(5),
    cost_eur                NUMERIC(10,2),       -- Summe aller Sub-Sessions
    data_source             VARCHAR(50) NOT NULL DEFAULT 'WALLBOX_GOE',
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_group_car_id ON charging_session_group(car_id);
CREATE INDEX idx_session_group_car_session_end ON charging_session_group(car_id, session_end);

-- Verknüpfung: ev_log → charging_session_group
-- NULL = normale Einzel-Session (kein Grouping)
-- NOT NULL = Teil einer Gruppe (Sub-Session, für Dashboard unterdrückt)
ALTER TABLE ev_log ADD COLUMN session_group_id UUID REFERENCES charging_session_group(id);

CREATE INDEX idx_ev_log_session_group_id ON ev_log(session_group_id) WHERE session_group_id IS NOT NULL;
