-- Hotfix: Die Unique-Constraint aus V24 zählte Soft-Delete-Tombstones (V188) mit.
-- Ein gelöschter Ladevorgang blockierte damit das erneute Eintragen zur selben Uhrzeit.
-- Ersatz durch partiellen Unique-Index, der nur aktive Zeilen betrachtet.
ALTER TABLE ev_log DROP CONSTRAINT uq_ev_log_car_loggedat_datasource;

CREATE UNIQUE INDEX uq_ev_log_car_loggedat_datasource
    ON ev_log (car_id, logged_at, data_source)
    WHERE deleted_at IS NULL;
