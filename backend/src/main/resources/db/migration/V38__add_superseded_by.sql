ALTER TABLE ev_log
  ADD COLUMN superseded_by UUID REFERENCES ev_log(id) ON DELETE SET NULL;

-- Index for findImportLogsInTimeWindow and findUserLoggedInTimeWindow
CREATE INDEX idx_ev_log_car_datasource_logged_at
    ON ev_log (car_id, data_source, logged_at)
    WHERE superseded_by IS NULL;
