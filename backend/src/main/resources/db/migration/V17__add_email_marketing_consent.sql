ALTER TABLE app_user
    ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT true;
