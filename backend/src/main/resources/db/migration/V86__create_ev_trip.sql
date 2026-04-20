CREATE TABLE ev_trip (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    car_id                  UUID NOT NULL,
    data_source             VARCHAR(30) NOT NULL,
    trip_started_at         TIMESTAMPTZ NOT NULL,
    trip_ended_at           TIMESTAMPTZ,
    soc_start               INT,
    soc_end                 INT,
    odometer_start_km       NUMERIC(10, 1),
    odometer_end_km         NUMERIC(10, 1),
    distance_km             NUMERIC(8, 1),
    location_start_geohash  VARCHAR(12),
    location_end_geohash    VARCHAR(12),
    outside_temp_celsius    NUMERIC(4, 1),
    nominal_full_pack_kwh   NUMERIC(6, 2),
    estimated_consumed_kwh  NUMERIC(6, 2),
    status                  VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    external_id             UUID,
    raw_payload             JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ev_trip_car_started ON ev_trip (car_id, trip_started_at DESC);
CREATE INDEX idx_ev_trip_user_started ON ev_trip (user_id, trip_started_at DESC);
CREATE UNIQUE INDEX idx_ev_trip_external_id ON ev_trip (external_id) WHERE external_id IS NOT NULL;
