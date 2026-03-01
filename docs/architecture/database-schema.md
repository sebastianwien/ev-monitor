# Database Schema

**Database:** PostgreSQL 15-alpine
**Last Updated:** 2026-03-01

## Tables

### app_user

**Purpose:** User Accounts

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | User ID |
| email | VARCHAR | UNIQUE, NOT NULL | Email (Login-Identifier) |
| username | VARCHAR(50) | UNIQUE, NOT NULL | Display Name (3-20 chars, alphanumeric + underscore) |
| password_hash | VARCHAR | | BCrypt hashed (nullable für OAuth-Only Accounts) |
| oauth_provider | VARCHAR | | GOOGLE \| FACEBOOK \| APPLE \| null |
| oauth_sub | VARCHAR | | Provider User ID |
| email_verified | BOOLEAN | NOT NULL, DEFAULT FALSE | Muss true sein für Login |
| role | VARCHAR | | USER \| ADMIN (future) |
| is_seed_data | BOOLEAN | DEFAULT FALSE | Flag für DevDataSeeder Test-Users |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indices:**
- `idx_app_user_email` (email)
- `idx_app_user_username` (username)

---

### email_verification_tokens

**Purpose:** Email-Verifizierung (24h TTL)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Token ID |
| user_id | UUID | FK → app_user ON DELETE CASCADE | |
| token | VARCHAR(64) | UNIQUE, NOT NULL | 256-bit Base64url SecureRandom |
| expires_at | TIMESTAMP | NOT NULL | 24h nach created_at |
| created_at | TIMESTAMP | NOT NULL | |

**Cleanup:** Tokens werden bei Verifikation gelöscht. Expired Tokens bleiben in DB (TODO: Scheduled Cleanup).

---

### car

**Purpose:** User's Electric Vehicles

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Car ID |
| user_id | UUID | FK → app_user, NOT NULL | Owner |
| model | VARCHAR | NOT NULL | CarBrand.CarModel enum als STRING |
| year | INTEGER | NOT NULL | Baujahr |
| license_plate | VARCHAR | | Kennzeichen (optional) |
| trim | VARCHAR | | Modellvariante (z.B. "Performance", "Long Range") |
| battery_capacity_kwh | NUMERIC(10,2) | NOT NULL | Batteriekapazität |
| power_kw | NUMERIC(10,2) | | Leistung in kW |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indices:**
- `idx_car_user_id` (user_id)

**WICHTIG:** `model` ist STRING (kein enum constraint in DB), ermöglicht neue Models ohne Migration.

---

### ev_log

**Purpose:** Charging Logs

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Log ID |
| car_id | UUID | FK → car ON DELETE CASCADE, NOT NULL | |
| kwh_charged | NUMERIC(10,2) | NOT NULL | Geladene Energie |
| cost_eur | NUMERIC(10,2) | NOT NULL | Kosten in Euro |
| charge_duration_minutes | INTEGER | NOT NULL | Ladedauer |
| geohash | VARCHAR(5) | | 5-char Geohash (~5km Präzision) |
| logged_at | TIMESTAMP | | Zeitpunkt des Ladevorgangs (kann Vergangenheit sein) |
| odometer_km | INTEGER | | Kilometerstand (optional) |
| max_charging_power_kw | NUMERIC(10,2) | | Max. Ladeleistung (z.B. 11, 50, 150 kW) |
| data_source | VARCHAR(50) | DEFAULT 'USER_LOGGED' | USER_LOGGED \| SPRITMONITOR_IMPORT \| TESLA_IMPORT |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**Indices:**
- `idx_ev_log_car_id` (car_id)
- `idx_ev_log_car_date` (car_id, logged_at DESC)
- `idx_ev_log_logged_at` (logged_at)
- `idx_ev_log_odometer` (car_id, odometer_km)

**Privacy:** `geohash` statt GPS-Koordinaten!

---

### coin_log

**Purpose:** Gamification - Coin Transactions

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Transaction ID |
| user_id | UUID | FK → app_user, NOT NULL | |
| amount | INTEGER | NOT NULL | Coin-Menge (kann negativ sein für Ausgaben) |
| coin_type | VARCHAR | NOT NULL | GREEN_COIN \| DISTANCE_COIN \| SOCIAL_COIN \| STREAK_COIN \| ACHIEVEMENT_COIN \| EFFICIENCY_COIN |
| action_description | VARCHAR | NOT NULL | Grund (z.B. "WLTP data contribution") |
| created_at | TIMESTAMP | NOT NULL | |

**Indices:**
- `idx_coin_log_user_id` (user_id)
- `idx_coin_log_created_at` (created_at DESC)

**Balance Calculation:** `SUM(amount) WHERE user_id = ?`

---

### vehicle_specification

**Purpose:** WLTP Crowdsourcing Data

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Spec ID |
| car_brand | VARCHAR | NOT NULL | CarBrand enum name (z.B. "TESLA") |
| car_model | VARCHAR | NOT NULL | CarModel enum name (z.B. "MODEL_3") |
| battery_capacity_kwh | NUMERIC(10,2) | NOT NULL | Batteriekapazität |
| wltp_range_km | NUMERIC(10,2) | NOT NULL | Offizielle WLTP Reichweite |
| wltp_consumption_kwh_per_100km | NUMERIC(10,2) | NOT NULL | Offizielle Verbrauchswerte |
| wltp_type | VARCHAR | NOT NULL | COMBINED \| HIGHWAY \| CITY |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**UNIQUE Constraint:** `(car_brand, car_model, battery_capacity_kwh, wltp_type)`

**Indices:**
- `idx_vehicle_spec_lookup` (car_brand, car_model, battery_capacity_kwh)

---

## Migrations (Flyway)

**Location:** `backend/src/main/resources/db/migration/`

**Naming:** `V{version}__{description}.sql` (z.B. `V1__baseline.sql`)

**Applied Migrations:**
1. `V1__baseline.sql` - Initial schema (users, cars, ev_logs, coin_logs, vehicle_specs)
2. `V2__add_composite_indices.sql` - Performance indices
3. `V3__remove_car_model_check_constraint.sql` - Allow dynamic car models
4. `V4__remove_remaining_enum_check_constraints.sql` - Remove all enum constraints
5. `V5__add_evlog_tracking_fields.sql` - odometer_km, max_charging_power_kw
6. `V6__add_username_support.sql` - username column + unique constraint
7. `V7__add_email_verification.sql` - email_verification_tokens table + email_verified flag
8. `V8__seed_wltp_data.sql` - Seed WLTP data (currently empty)
9. `V9__add_seed_data_flag.sql` - is_seed_data flag für DevDataSeeder
10. `V10__add_data_source_to_ev_log.sql` - data_source column
11. `V11__create_tesla_connections.sql` - tesla_connection table (TODO: Tesla API Integration)

**Checksum Validation:** Flyway prüft bei jedem Start ob Migrations geändert wurden. Bei Mismatch → Fehler (Fix: `flywayRepair` oder DB-Reset).

---

## Foreign Key Constraints

- `car.user_id` → `app_user.id` (CASCADE on delete? NO - orphaned cars bleiben)
- `ev_log.car_id` → `car.id` (CASCADE on delete)
- `coin_log.user_id` → `app_user.id` (CASCADE on delete? NO)
- `email_verification_tokens.user_id` → `app_user.id` (CASCADE on delete)

**WICHTIG:** Bei User-Löschung bleiben Cars/Coins in DB (für Statistiken). TODO: GDPR-compliant Löschung.

---

## Related Docs
- [Authentication](../features/authentication.md) - email_verification_tokens
- [Charging Logs](../features/charging-logs.md) - ev_log
- [WLTP Crowdsourcing](../features/wltp-crowdsourcing.md) - vehicle_specification, coin_log
