-- Prevent duplicate imports: same car, same session start time, same data source
ALTER TABLE ev_log
    ADD CONSTRAINT uq_ev_log_car_loggedat_datasource
    UNIQUE (car_id, logged_at, data_source);
