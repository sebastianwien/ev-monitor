-- Trip-Stories: User-Reiseberichte mit gesnapshotteten Trip-Widgets (docs/features/trip-stories.md)
CREATE TABLE trip_story (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title        VARCHAR(160) NOT NULL,
    slug         VARCHAR(200) NOT NULL UNIQUE,
    summary      VARCHAR(300),
    language     VARCHAR(5)   NOT NULL DEFAULT 'de',
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    blocks       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX ON trip_story (user_id);
CREATE INDEX ON trip_story (status, published_at DESC);
