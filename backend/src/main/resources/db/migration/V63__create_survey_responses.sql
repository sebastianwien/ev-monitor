CREATE TABLE survey_response (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_slug   VARCHAR(100)  NOT NULL,
    user_id       UUID          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    answers       JSONB         NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (survey_slug, user_id)
);

CREATE INDEX idx_survey_response_slug ON survey_response(survey_slug);
