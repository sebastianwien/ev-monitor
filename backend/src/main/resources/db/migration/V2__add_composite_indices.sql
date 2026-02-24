-- Add composite indices for better query performance
-- Generated: 2026-02-24
-- Description: Performance optimization - composite indices for common query patterns

-- Composite index for user-specific car queries with status filter
-- Useful for: "SELECT * FROM car WHERE user_id = ? AND status = 'ACTIVE'"
CREATE INDEX IF NOT EXISTS idx_car_user_status ON car(user_id, status);

-- Composite index for date-range queries on charging logs
-- Useful for: "SELECT * FROM ev_log WHERE car_id = ? AND logged_at BETWEEN ? AND ?"
CREATE INDEX IF NOT EXISTS idx_ev_log_car_date ON ev_log(car_id, logged_at DESC);

-- Index for coin balance calculations (sum by user)
-- Useful for: "SELECT SUM(amount) FROM coin_log WHERE user_id = ?"
CREATE INDEX IF NOT EXISTS idx_coin_log_user_created ON coin_log(user_id, created_at DESC);
