CREATE TABLE tessie_raw_imports (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user(id),
    vin         VARCHAR(17)  NOT NULL,
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('drive', 'charge')),
    tessie_id   BIGINT       NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL,
    raw         JSONB        NOT NULL,
    imported_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed   BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, vin, type, tessie_id)
);

CREATE INDEX ON tessie_raw_imports (user_id, vin, type);
CREATE INDEX ON tessie_raw_imports (processed) WHERE NOT processed;
