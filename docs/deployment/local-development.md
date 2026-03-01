# Local Development Setup

**Last Updated:** 2026-03-01

## Quick Start

```bash
./dev.sh
```

Das startet:
- ✅ PostgreSQL (Docker, Port 5432)
- ✅ Mailpit (Docker, Port 8025 Web UI)
- ✅ Backend (nativ, Port 8080)
- ✅ Frontend (nativ, Port 5173)

**Zugriff:**
- 📱 **Frontend:** http://localhost:5173
- 🔧 **Backend API:** http://localhost:8080
- 📧 **Mailpit UI:** http://localhost:8025
- 🗄️ **Database:** localhost:5432 (user: evmonitor, pass: evmonitor, db: ev_monitor)

**Test Users (DevDataSeeder):**
- max@ev-monitor.net / `123!"§`
- anna@ev-monitor.net / `123!"§`
- kurt@ev-monitor.net / `123!"§`

Jeder User hat 2 Autos mit ~70-80 Logs über 1 Jahr.

---

## dev.sh im Detail

**Was macht es:**
1. Startet PostgreSQL via `docker-compose.dev.yml`
2. Wartet auf DB-Readiness (`pg_isready`)
3. Fragt: "Drop all tables for fresh start?" (Y/N)
4. Startet Backend mit `SPRING_PROFILES_ACTIVE=dev` (DevDataSeeder läuft automatisch)
5. Startet Frontend via `npm run dev`
6. Tailed Logs in `logs/backend.log` und `logs/frontend.log`

**Stoppen:**
```bash
./stop-dev.sh
# Oder: Ctrl+C im Terminal
```

---

## Docker Compose Files

| File | Zweck | Services |
|------|-------|----------|
| `docker-compose.dev.yml` | **Dev (empfohlen)** | DB + Mailpit |
| `docker-compose.local.yml` | **Full-Stack lokal** | DB + Backend + Frontend + Nginx + Mailpit |
| `docker-compose.yml` | **Production** | DB + Backend + Nginx + Certbot |
| `docker-compose.frontend-dev.yml` | **Frontend Hot Reload** | Nur Frontend Dev Server |

**Typischer Workflow:**
```bash
# 1. Infrastruktur (DB + Mail)
docker compose -f docker-compose.dev.yml up -d

# 2. Backend nativ (Hot Reload)
cd backend && ./gradlew bootRun

# 3. Frontend nativ (Hot Reload)
cd frontend && npm run dev

# 4. Mailpit Web UI: http://localhost:8025
```

---

## Environment Variables

### Backend (dev.sh setzt diese)
- `SPRING_PROFILES_ACTIVE=dev` - Aktiviert DevDataSeeder
- `JWT_SECRET` - NICHT gesetzt (Backend nutzt default aus `application.yml`)

### Frontend (.env.local)
```bash
VITE_API_BASE_URL=http://localhost:8080/api
```

**WICHTIG:** Vite lädt `.env` nur beim Start! Nach Änderungen Frontend neu starten.

---

## Database Reset

**Option 1: Via dev.sh**
```bash
./dev.sh
# Bei "Drop all tables?" → Y
```

**Option 2: Manuell**
```bash
docker compose -f docker-compose.dev.yml exec -T db psql -U evmonitor -d ev_monitor <<EOF
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO evmonitor;
GRANT ALL ON SCHEMA public TO public;
EOF
```

**Option 3: Docker Volume löschen**
```bash
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
```

---

## Flyway Migrations

**Location:** `backend/src/main/resources/db/migration/`

**Auto-Run:** Beim Backend-Start via Spring Boot Flyway Integration.

**Repair (bei Checksum Mismatch):**
```bash
cd backend && ./gradlew flywayRepair
```

**WICHTIG:** Migrations **niemals** nachträglich ändern! Bei Fehlern neue Migration erstellen.

---

## DevDataSeeder

**Component:** `backend/src/main/java/com/evmonitor/infrastructure/seed/DevDataSeeder.java`

**Triggered by:** `@Profile("dev")` + `SPRING_PROFILES_ACTIVE=dev`

**Was wird erstellt:**
- 3 Test-Users (max, anna, kurt) mit `is_seed_data = true`
- 6 Autos (2 per User, verschiedene Modelle)
- ~370 Charging Logs über 1 Jahr mit:
  - Seasonalen Verbrauchswerten (Winter +28%, Sommer -8%)
  - Realistischen Odometer-Daten
  - Random Berlin Geohashes (Wedding, Mitte, Prenzlauer Berg, etc.)
  - Max Charging Power (11kW, 50kW, 150kW)

**Idempotent:** Checkt `userRepository.findByEmail("test1@ev-monitor.net")` - wenn vorhanden, wird nichts erstellt.

---

## Ports & Services

| Service | Port | URL |
|---------|------|-----|
| Frontend (Vite) | 5173 | http://localhost:5173 |
| Backend (Spring Boot) | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | jdbc:postgresql://localhost:5432/ev_monitor |
| Mailpit Web UI | 8025 | http://localhost:8025 |
| Mailpit SMTP | 1025 | (Backend nutzt dies für Email-Versand) |

---

## Troubleshooting

### Port 8080 already in use
```bash
lsof -ti:8080 | xargs kill -9
./dev.sh
```

### Flyway Checksum Mismatch
```bash
cd backend && ./gradlew flywayRepair
# Oder: DB reset (siehe oben)
```

### Frontend zeigt keine Daten / API Calls schlagen fehl
**Prüfe:**
1. Backend läuft auf Port 8080: `curl http://localhost:8080/actuator/health`
2. `.env.local` existiert mit `VITE_API_BASE_URL=http://localhost:8080/api`
3. Frontend neu starten (Vite lädt .env nur beim Start!)

### Email-Verifizierung funktioniert nicht
**Mailpit Web UI:** http://localhost:8025 - dort sollten alle gesendeten Emails landen.

---

## Related Docs
- [Database Schema](../architecture/database-schema.md) - Tabellen & Migrations
- [Authentication](../features/authentication.md) - Test-User Login
