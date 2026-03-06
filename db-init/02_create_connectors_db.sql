-- Creates the connectors service database and user.
-- Runs automatically when the PostgreSQL container starts for the first time.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'connectors_user') THEN
        CREATE ROLE connectors_user WITH LOGIN PASSWORD 'connectors_password_change_me';
    END IF;
END
$$;

SELECT 'CREATE DATABASE ev_connectors OWNER connectors_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ev_connectors')\gexec

GRANT ALL PRIVILEGES ON DATABASE ev_connectors TO connectors_user;
