-- Korrigiert Ladevorgaenge, die faelschlich als Heimladung gelten.
--
-- Ursache: ev_log.is_public_charging ist NOT NULL DEFAULT false. Jedes Log ohne
-- explizite Angabe zaehlt damit als Heimladung. Bei automatisch erzeugten Logs
-- (Telemetrie, Importe) setzt niemand das Flag.
--
-- Kriterien:
--   DC          - eine Gleichstromladung ist nie eine Heimladung
--   > 12 kW     - oberhalb dessen laedt praktisch keine Hauswallbox
--
-- Gegenprobe auf Prod: die Treffer haben einen Medianpreis von 0,43 EUR/kWh,
-- der verbleibende Rest 0,28 EUR/kWh - also oeffentliches Preisniveau.
--
-- Ausnahmen:
--   WALLBOX_GOE - AC-Hardware, dort ist der charging_type falsch, nicht der Ort
--   Foxcar      - Testaccounts
--   5 Fahrzeuge - siehe unten: belegte Heimlader oberhalb 12 kW
--
-- Rueckrollbar ueber ev_log_public_flag_backup (Statement am Dateiende).

CREATE TABLE ev_log_public_flag_backup (
    ev_log_id      UUID PRIMARY KEY,
    previous_value BOOLEAN     NOT NULL,
    reason         VARCHAR(64) NOT NULL,
    corrected_at   TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE ev_log_public_flag_backup IS
    'Vorzustand der von V165 korrigierten is_public_charging-Flags.';

INSERT INTO ev_log_public_flag_backup (ev_log_id, previous_value, reason)
SELECT e.id,
       e.is_public_charging,
       CASE WHEN e.charging_type = 'DC' THEN 'DC' ELSE 'POWER_GT_12KW' END
FROM ev_log e
JOIN car c      ON c.id = e.car_id
JOIN app_user u ON u.id = c.user_id
WHERE e.is_public_charging = false
  AND e.data_source <> 'WALLBOX_GOE'
  AND u.email NOT ILIKE '%foxcar%'
  AND (
        e.charging_type = 'DC'
        -- Diese Fahrzeuge laden nachweislich daheim oberhalb von 12 kW: alle
        -- Treffer liegen an genau einem Ort, zu Heim-Preisniveau (0,003 bis
        -- 0,29 EUR/kWh). Warum die Leistung so hoch gemeldet wird, ist offen -
        -- moeglicherweise Spitzen- statt Durchschnittswert. Ihre DC-Ladungen
        -- werden weiterhin korrigiert, nur das Leistungskriterium greift nicht.
     OR (e.max_charging_power_kw > 12 AND c.id NOT IN (
            'd5f9bf85-1860-42d8-994d-a747544ba897',
            'aac452c0-5989-454d-9ad8-3e28a6f17e80',
            '2f9ffe58-bad4-4ac7-86be-bdd8d61b3fd8',
            'acf9f4c6-9eea-4022-bb49-dec31505f7a1',
            '80fdf798-dbc5-4c12-aab1-fe03aa3f3472'
        ))
  );

UPDATE ev_log e
SET is_public_charging = true
FROM ev_log_public_flag_backup b
WHERE b.ev_log_id = e.id;

-- Rueckrollen (manuell, nicht Teil der Migration):
--   UPDATE ev_log e SET is_public_charging = b.previous_value
--   FROM ev_log_public_flag_backup b WHERE b.ev_log_id = e.id;
