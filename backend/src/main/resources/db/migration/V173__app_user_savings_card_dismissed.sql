-- Ausgeblendete Ersparnis-Kachel je Nutzer. Standard: sichtbar - wer nichts ausblendet,
-- sieht die Kachel weiterhin (sofern berechtigt und relevant).
ALTER TABLE app_user
    ADD COLUMN savings_card_dismissed BOOLEAN NOT NULL DEFAULT FALSE;
