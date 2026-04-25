ALTER TABLE ev_trip
    ADD COLUMN user_edited_at TIMESTAMPTZ,
    ADD COLUMN user_created   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feedback       TEXT;
