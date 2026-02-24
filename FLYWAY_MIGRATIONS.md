# Flyway Database Migrations

## Overview

Flyway manages database schema changes through versioned SQL migration files. This ensures:
- ✅ **Version control** for database schema
- ✅ **Automated migrations** on deployment
- ✅ **Rollback capability**
- ✅ **Audit trail** of all changes

---

## Migration Files Location

```
backend/src/main/resources/db/migration/
├── V1__baseline.sql          # Initial schema
├── V2__add_composite_indices.sql  # Performance optimization
└── V3__your_next_migration.sql    # Future changes
```

---

## Naming Convention

Migration files must follow this pattern:

```
V{version}__{description}.sql
```

**Examples:**
- `V1__baseline.sql`
- `V2__add_user_preferences.sql`
- `V3__add_charging_station_table.sql`
- `V4__rename_column_kwh_to_energy.sql`

**Rules:**
- Prefix: `V` (uppercase)
- Version: Integer (1, 2, 3, ...) - can also use dots (1.0, 1.1)
- Separator: Two underscores `__`
- Description: Snake_case or lowercase with underscores
- File extension: `.sql`

---

## How Flyway Works

1. **Startup**: Spring Boot starts, Flyway runs before app initialization
2. **Check**: Flyway reads `flyway_schema_history` table
3. **Compare**: Compares migration files with applied migrations
4. **Apply**: Executes new migrations in order
5. **Record**: Updates `flyway_schema_history` with success/failure

### Flyway Schema History Table

```sql
SELECT * FROM flyway_schema_history;
```

| installed_rank | version | description | type | script | checksum | installed_by | installed_on | execution_time | success |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 0 | << Flyway Baseline >> | BASELINE | | | evmonitor | 2026-02-24 10:00:00 | 0 | true |
| 2 | 1 | baseline | SQL | V1__baseline.sql | 123456789 | evmonitor | 2026-02-24 10:00:01 | 45 | true |
| 3 | 2 | add composite indices | SQL | V2__add_composite_indices.sql | 987654321 | evmonitor | 2026-02-24 10:00:02 | 12 | true |

---

## Creating a New Migration

### Step 1: Create the SQL file

```bash
cd backend/src/main/resources/db/migration
touch V3__add_user_avatar.sql
```

### Step 2: Write the migration

```sql
-- Add avatar column to users table
-- Generated: 2026-02-24
-- Description: Allow users to upload profile pictures

ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_app_user_avatar ON app_user(avatar_url);
```

### Step 3: Test locally

```bash
# Start local database
docker compose -f docker-compose.local.yml up --build

# Check logs
docker compose -f docker-compose.local.yml logs backend | grep "Flyway"
```

### Step 4: Commit & Push

```bash
git add backend/src/main/resources/db/migration/V3__add_user_avatar.sql
git commit -m "feat: add user avatar column"
git push origin main
# GitHub Actions will deploy automatically
```

---

## Configuration

### application.yml (Production)

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true  # For existing databases
    baseline-version: 0
    locations: classpath:db/migration

  jpa:
    hibernate:
      ddl-auto: validate  # ⚠️ CHANGED from "update"!
```

**Important Changes:**
- `ddl-auto: validate` - Hibernate no longer auto-generates schema
- `baseline-on-migrate: true` - Allows Flyway to work with existing databases

### application-dev.yml (Development)

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: false  # Allow `flyway clean` in dev

  jpa:
    hibernate:
      ddl-auto: validate
```

---

## Common Operations

### View Migration History

```bash
# SSH into server
ssh ihle@46.225.210.231
cd /opt/ev-monitor/ev-monitor

# Connect to database
docker compose exec db psql -U evmonitor -d ev_monitor

# Query history
SELECT installed_rank, version, description, script, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Manually Trigger Migration (if needed)

```bash
# SSH into server
cd /opt/ev-monitor/ev-monitor

# Restart backend (will run pending migrations)
docker compose restart backend

# Check logs
docker compose logs backend | grep -i "flyway"
```

### Reset Database (Development Only!)

```bash
# LOCAL ONLY! Never run on production!
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up --build
```

---

## Rollback Strategy

Flyway doesn't support automatic rollback, but you can:

### Option 1: Create a "Undo" Migration

```sql
-- V4__add_column.sql
ALTER TABLE app_user ADD COLUMN phone VARCHAR(20);
```

```sql
-- V5__undo_add_column.sql (if V4 was a mistake)
ALTER TABLE app_user DROP COLUMN phone;
```

### Option 2: Restore from Backup

```bash
# GitHub Actions creates backups automatically before each deployment
cd /opt/ev-monitor/ev-monitor
ls -lh backup-*.sql

# Restore
docker compose exec -T db psql -U evmonitor -d ev_monitor < backup-20260224-120000.sql
```

### Option 3: Manual Repair

```bash
# If migration failed mid-way
docker compose exec db psql -U evmonitor -d ev_monitor

# Check failed migrations
SELECT * FROM flyway_schema_history WHERE success = false;

# Delete failed entry (allows retry)
DELETE FROM flyway_schema_history WHERE version = '3' AND success = false;

# Restart backend
docker compose restart backend
```

---

## Best Practices

### ✅ DO:
- Write idempotent migrations (use `IF NOT EXISTS`, `IF EXISTS`)
- Test migrations locally before deploying
- Keep migrations small and focused
- Add comments explaining the change
- Create backups before production migrations
- Version control all migrations

### ❌ DON'T:
- Never modify an already-applied migration file
- Never delete migration files from version control
- Never run `flyway clean` on production (drops all data!)
- Don't mix DDL and DML in one migration (if possible)
- Don't rely on default values - be explicit

---

## Example Migrations

### Add a new table

```sql
-- V3__add_charging_station.sql
CREATE TABLE charging_station (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    geohash VARCHAR(5) NOT NULL,
    power_kw NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_charging_station_geohash ON charging_station(geohash);
```

### Add a new column

```sql
-- V4__add_user_preferences.sql
ALTER TABLE app_user ADD COLUMN language VARCHAR(10) DEFAULT 'en';
ALTER TABLE app_user ADD COLUMN theme VARCHAR(20) DEFAULT 'light';
```

### Rename a column (safe way)

```sql
-- V5__rename_kwh_to_energy.sql
-- Step 1: Add new column
ALTER TABLE ev_log ADD COLUMN energy_kwh NUMERIC(10,2);

-- Step 2: Copy data
UPDATE ev_log SET energy_kwh = kwh_charged WHERE energy_kwh IS NULL;

-- Step 3: Make new column NOT NULL
ALTER TABLE ev_log ALTER COLUMN energy_kwh SET NOT NULL;

-- Step 4: Drop old column (after ensuring app uses new column)
ALTER TABLE ev_log DROP COLUMN kwh_charged;
```

### Data migration

```sql
-- V6__seed_popular_vehicles.sql
INSERT INTO vehicle_specification (id, car_brand, car_model, battery_capacity_kwh, wltp_range_km, wltp_consumption_kwh_per_100km, wltp_type, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'TESLA', 'MODEL_3', 57.5, 491, 15.4, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'VOLKSWAGEN', 'ID_3', 58.0, 420, 15.3, 'COMBINED', NOW(), NOW()),
    (gen_random_uuid(), 'HYUNDAI', 'IONIQ_5', 77.4, 507, 16.8, 'COMBINED', NOW(), NOW())
ON CONFLICT (car_brand, car_model, battery_capacity_kwh, wltp_type) DO NOTHING;
```

---

## Troubleshooting

### Error: "Migration checksum mismatch"

**Cause:** Migration file was modified after being applied.

**Solution:**
```sql
-- Update checksum in flyway_schema_history
UPDATE flyway_schema_history SET checksum = NULL WHERE version = '2';
```

### Error: "Found non-empty schema(s) without schema history table"

**Cause:** Database already has tables but no Flyway history.

**Solution:** Already configured via `baseline-on-migrate: true` in `application.yml`.

### Error: "Migration failed: Syntax error in SQL"

**Cause:** Invalid SQL in migration file.

**Solution:**
1. Fix the SQL file
2. Delete failed migration from history (see Manual Repair above)
3. Restart backend

---

## Migration Checklist

Before deploying a new migration:

- [ ] Migration file follows naming convention
- [ ] SQL is idempotent (uses `IF NOT EXISTS`)
- [ ] Migration tested locally
- [ ] Database backup exists
- [ ] Rollback plan documented
- [ ] Deployment scheduled during low-traffic period
- [ ] Team notified about schema changes
