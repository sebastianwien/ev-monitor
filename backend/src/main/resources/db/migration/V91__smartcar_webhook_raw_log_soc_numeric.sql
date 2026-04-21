ALTER TABLE smartcar_webhook_raw_log
    ALTER COLUMN soc_percent TYPE NUMERIC(5,2) USING soc_percent::NUMERIC(5,2);
