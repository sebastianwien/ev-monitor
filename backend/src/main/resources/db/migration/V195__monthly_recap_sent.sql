-- Monatsrückblick-Mail: ein Eintrag pro Nutzer und Monat.
-- Der Job legt die Zeile VOR dem Versand an (INSERT ... ON CONFLICT DO NOTHING) und löscht sie,
-- wenn der Versand scheitert. Damit verschickt auch bei Blue/Green (zwei Instanzen kurz parallel)
-- und beim Nachhol-Lauf am 3. des Monats niemand eine Mail doppelt.
CREATE TABLE monthly_recap_sent (
    user_id UUID      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    month   DATE      NOT NULL,
    car_id  UUID      NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, month)
);
