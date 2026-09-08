-- XPeng-Import: DB-Tabelle ist die Warteschlange (statt In-Memory-Executor).
-- Der Worker braucht Tempfile-Pfad, Format und (nur XLSX) das Datei-Passwort aus der Job-Zeile.
ALTER TABLE xpeng_import_job
    ADD COLUMN tempfile_path TEXT,
    ADD COLUMN format VARCHAR(20),
    ADD COLUMN file_password VARCHAR(255);

CREATE INDEX idx_xpeng_import_job_queued ON xpeng_import_job (created_at) WHERE status = 'QUEUED';
