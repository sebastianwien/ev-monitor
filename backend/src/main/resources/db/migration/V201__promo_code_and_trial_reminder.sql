-- Aktionscodes mit längerem Trial (z. B. FIRSTEV: 28 statt 7 Tage, nur für Konten aus dem firstev-Forum).
-- Eingelöst gilt ein Code erst, wenn Stripe das Trial startet (Webhook), abgebrochene Checkouts zählen nicht.
CREATE TABLE promo_code (
    code                 VARCHAR(40)  PRIMARY KEY,
    trial_days           INT          NOT NULL,
    max_redemptions      INT          NOT NULL,
    required_utm_source  VARCHAR(100),
    valid_until          TIMESTAMP,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Ein Code je Konto. Konto gelöscht: Einlösung weg (CASCADE), das Kontingent zählt nur bestehende Konten.
CREATE TABLE promo_redemption (
    user_id      UUID         PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    code         VARCHAR(40)  NOT NULL REFERENCES promo_code (code),
    redeemed_at  TIMESTAMP    NOT NULL
);
CREATE INDEX idx_promo_redemption_code ON promo_redemption (code);

INSERT INTO promo_code (code, trial_days, max_redemptions, required_utm_source)
VALUES ('FIRSTEV', 28, 100, 'firstev');

-- Erinnerung 7 Tage vor Ende eines langen Trials. Wird beim Versand auf NULL gesetzt.
ALTER TABLE app_user ADD COLUMN trial_reminder_due_at TIMESTAMP;
