-- API Key System für externe Uploads (Wallboxen, Skripte, Home-Automation)
-- Plaintext Key wird NIEMALS gespeichert — nur SHA-256 Hash + 8-Zeichen Prefix für UI

CREATE TABLE api_key (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    key_hash     VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 des Plaintext-Keys (Hex)
    key_prefix   VARCHAR(8)  NOT NULL,         -- z.B. "evm_a1b2" — nur für UI-Anzeige
    name         VARCHAR(100),                  -- z.B. "OpenWB Zuhause"
    last_used_at TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_key_user_id ON api_key(user_id);
CREATE INDEX idx_api_key_key_hash ON api_key(key_hash);
