CREATE INDEX idx_ev_log_car_measurement_loggedat
    ON ev_log(car_id, measurement_type, logged_at DESC)
    WHERE superseded_by IS NULL;
