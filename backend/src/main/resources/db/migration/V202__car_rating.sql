-- Fahrzeugbewertung Phase 1: eine Erstbewertung je Auto (car_rating) plus optionale Erfahrungstexte
-- (car_review), die erst nach händischer Freigabe öffentlich werden.
-- Rein additiv (Expand). Kein CASCADE vom Auto: wird ein Auto endgültig gelöscht, bleibt die Bewertung
-- anonym für die Modellstatistik erhalten (car_id wird NULL). Texte mit delete_with_car räumt der Purge vorher ab.
-- V199 bis V201 sind vom firstev-Plan belegt.

CREATE TABLE car_rating (
    id                       UUID PRIMARY KEY,
    car_id                   UUID         REFERENCES car(id) ON DELETE SET NULL,
    user_id                  UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    car_model                VARCHAR(100) NOT NULL,
    vehicle_specification_id UUID,
    version                  INTEGER      NOT NULL DEFAULT 1,
    rebuy                    VARCHAR(20)  NOT NULL CHECK (rebuy IN ('YES', 'BRAND_OTHER_MODEL', 'NO')),
    rebuy_reasons            TEXT[],
    daily_fit                INTEGER      NOT NULL CHECK (daily_fit BETWEEN 1 AND 5),
    long_distance            INTEGER      CHECK (long_distance BETWEEN 1 AND 5),
    software                 INTEGER      NOT NULL CHECK (software BETWEEN 1 AND 5),
    build_quality            INTEGER      NOT NULL CHECK (build_quality BETWEEN 1 AND 5),
    service                  INTEGER      CHECK (service BETWEEN 1 AND 5),
    total_cost               VARCHAR(20)  NOT NULL CHECK (total_cost IN ('CHEAPER', 'EXPECTED', 'PRICIER')),
    strengths                TEXT[]       CHECK (cardinality(strengths) <= 3),
    weaknesses               TEXT[]       CHECK (cardinality(weaknesses) <= 3),
    km_driven                INTEGER      NOT NULL,
    log_count                INTEGER      NOT NULL,
    business_car             BOOLEAN      NOT NULL,
    seed                     BOOLEAN      NOT NULL,
    anonymized_at            TIMESTAMP,
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_car_rating_car_version ON car_rating(car_id, version) WHERE car_id IS NOT NULL;
CREATE INDEX idx_car_rating_car_model ON car_rating(car_model);
CREATE INDEX idx_car_rating_user ON car_rating(user_id);

COMMENT ON TABLE car_rating IS 'Fahrerbewertung je Auto. Bleibt nach Auto- und Kontolöschung anonym erhalten (car_id/user_id NULL, anonymized_at).';
COMMENT ON COLUMN car_rating.car_model IS 'Snapshot von car.model, damit anonymisierte Bewertungen weiter im Modell zählen.';
COMMENT ON COLUMN car_rating.km_driven IS 'Eigene Erfahrung: höchster minus niedrigster odometer_km der nicht gelöschten Logs beim Absenden.';

CREATE TABLE car_review (
    id               UUID PRIMARY KEY,
    car_rating_id    UUID        NOT NULL REFERENCES car_rating(id) ON DELETE CASCADE,
    body             TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 5000),
    pros             TEXT[],
    cons             TEXT[],
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    published_body   TEXT,
    rejection_reason VARCHAR(30),
    rejection_note   TEXT,
    llm_check        JSONB,
    delete_with_car  BOOLEAN     NOT NULL DEFAULT false,
    proof_km_driven  INTEGER,
    proof_log_count  INTEGER,
    proof_consumption NUMERIC(5,2),
    reviewed_by      UUID        REFERENCES app_user(id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_car_review_status ON car_review(status);
CREATE INDEX idx_car_review_rating ON car_review(car_rating_id);

COMMENT ON TABLE car_review IS 'Erfahrungstext zu einer Bewertung. Jede Fassung eine Zeile, öffentlich nur APPROVED (published_body).';
