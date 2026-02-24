-- Baseline: Current Production Schema
-- Generated: 2026-02-24
-- Description: Initial schema with all tables for EV Monitor application

-- Users table
CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    auth_provider VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);

-- Cars table
CREATE TABLE IF NOT EXISTS car (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    model VARCHAR(255) NOT NULL,
    manufacture_year INTEGER NOT NULL,
    license_plate VARCHAR(50),
    trim VARCHAR(100),
    battery_capacity_kwh NUMERIC(10,2),
    power_kw NUMERIC(10,2),
    registration_date DATE,
    deregistration_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_car_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_car_user_id ON car(user_id);
CREATE INDEX IF NOT EXISTS idx_car_status ON car(status);

-- EV Logs table
CREATE TABLE IF NOT EXISTS ev_log (
    id UUID PRIMARY KEY,
    car_id UUID NOT NULL,
    kwh_charged NUMERIC(10,2) NOT NULL,
    cost_eur NUMERIC(10,2) NOT NULL,
    charge_duration_minutes INTEGER NOT NULL,
    geohash VARCHAR(5),
    logged_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_ev_log_car FOREIGN KEY (car_id)
        REFERENCES car(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ev_log_car_id ON ev_log(car_id);
CREATE INDEX IF NOT EXISTS idx_ev_log_logged_at ON ev_log(logged_at);

-- Coin Logs table
CREATE TABLE IF NOT EXISTS coin_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    coin_type VARCHAR(30) NOT NULL,
    amount INTEGER NOT NULL,
    action_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_coin_log_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_coin_log_user_id ON coin_log(user_id);

-- Vehicle Specifications table
CREATE TABLE IF NOT EXISTS vehicle_specification (
    id UUID PRIMARY KEY,
    car_brand VARCHAR(100) NOT NULL,
    car_model VARCHAR(100) NOT NULL,
    battery_capacity_kwh NUMERIC(10,2) NOT NULL,
    wltp_range_km NUMERIC(10,2) NOT NULL,
    wltp_consumption_kwh_per_100km NUMERIC(10,2) NOT NULL,
    wltp_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_vehicle_spec
        UNIQUE(car_brand, car_model, battery_capacity_kwh, wltp_type)
);

CREATE INDEX IF NOT EXISTS idx_vehicle_spec_lookup
    ON vehicle_specification(car_brand, car_model, battery_capacity_kwh);
