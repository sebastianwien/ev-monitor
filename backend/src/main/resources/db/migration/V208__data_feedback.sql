-- "Korrekt erkannt?": Nutzerbewertung automatisch erfasster Ladevorgänge und Fahrten an einer Stelle,
-- damit die Datenqualität je Quelle in einer Abfrage auswertbar ist. Ersetzt ev_trip.feedback (Freitext).
-- Rein additiv (Expand). ev_trip.feedback bleibt bis zum Folgerelease stehen und wird dann entfernt.
-- Kein FK auf den Gegenstand (polymorph: ev_log oder ev_trip). Mit dem Konto verschwindet die Bewertung.

CREATE TABLE data_feedback (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    subject_type VARCHAR(10)  NOT NULL CHECK (subject_type IN ('LOG', 'TRIP')),
    subject_id   UUID         NOT NULL,
    data_source  VARCHAR(40)  NOT NULL,
    rating       VARCHAR(10)  NOT NULL CHECK (rating IN ('POSITIVE', 'NEGATIVE')),
    tags         TEXT[]       NOT NULL DEFAULT '{}',
    comment      TEXT         CHECK (char_length(comment) <= 1000),
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_data_feedback_subject ON data_feedback(subject_type, subject_id);
CREATE INDEX idx_data_feedback_user ON data_feedback(user_id);
CREATE INDEX idx_data_feedback_source ON data_feedback(data_source, rating);

COMMENT ON COLUMN data_feedback.data_source IS 'Quelle des Gegenstands beim Bewerten, für die Auswertung je Quelle ohne Join.';

-- Bisherige Fahrten-Bewertungen übernehmen. Alter Freitext: "positive" oder
-- "negative[ | tag1,tag2][ | Kommentar]"; ein zweiter Teil aus bekannten Tags gilt als Tag-Liste.
INSERT INTO data_feedback (id, user_id, subject_type, subject_id, data_source, rating, tags, comment, created_at, updated_at)
SELECT gen_random_uuid(), t.user_id, 'TRIP', t.id, t.data_source,
       CASE WHEN t.feedback LIKE 'positive%' THEN 'POSITIVE' ELSE 'NEGATIVE' END,
       CASE WHEN t.feedback LIKE 'negative%' AND p.second ~ '^(distance_wrong|time_wrong|duplicate|other)(,(distance_wrong|time_wrong|duplicate|other))*$'
            THEN string_to_array(upper(p.second), ',') ELSE '{}' END,
       CASE WHEN t.feedback NOT LIKE 'negative%' THEN NULL
            WHEN p.second ~ '^(distance_wrong|time_wrong|duplicate|other)(,(distance_wrong|time_wrong|duplicate|other))*$'
                THEN NULLIF(left(p.rest, 1000), '')
            ELSE NULLIF(left(concat_ws(' | ', p.second, NULLIF(p.rest, '')), 1000), '') END,
       now(), now()
FROM ev_trip t
CROSS JOIN LATERAL (
    SELECT nullif(trim(split_part(t.feedback, ' | ', 2)), '') AS second,
           nullif(trim(substring(t.feedback FROM length(split_part(t.feedback, ' | ', 1)) + length(split_part(t.feedback, ' | ', 2)) + 7)), '') AS rest
) p
WHERE t.feedback IS NOT NULL
  AND (t.feedback LIKE 'positive%' OR t.feedback LIKE 'negative%')
  AND t.user_id IS NOT NULL
  AND t.data_source IS NOT NULL
ON CONFLICT DO NOTHING;
