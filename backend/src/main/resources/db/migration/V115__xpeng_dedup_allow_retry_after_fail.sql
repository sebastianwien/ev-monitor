-- Erlaubt erneuten Upload derselben Datei, nachdem ein vorheriger Versuch FAILED ist.
-- Dedup-Logik im Service prueft nur noch [QUEUED, PROCESSING, DONE] - der partielle
-- Unique-Index zieht jetzt nach, damit App- und DB-Layer konsistent sind.
DROP INDEX IF EXISTS uq_xpeng_job_user_filehash;

CREATE UNIQUE INDEX uq_xpeng_job_user_filehash
    ON xpeng_import_job(user_id, file_hash)
    WHERE file_hash IS NOT NULL AND status <> 'FAILED';
