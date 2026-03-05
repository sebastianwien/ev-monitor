-- Creates the wallbox service database and user.
-- This script runs automatically when the PostgreSQL container starts for the first time.
-- The main ev_monitor database is created by POSTGRES_DB in docker-compose.

-- Create wallbox user (ignore if already exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'wallbox_user') THEN
        CREATE ROLE wallbox_user WITH LOGIN PASSWORD 'wallbox_password_change_me';
    END IF;
END
$$;

-- Create wallbox database (ignore if already exists)
SELECT 'CREATE DATABASE ev_monitor_wallbox OWNER wallbox_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ev_monitor_wallbox')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE ev_monitor_wallbox TO wallbox_user;
