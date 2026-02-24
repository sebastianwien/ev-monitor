# Local Development Setup

Für lokale Entwicklung brauchst du **NICHT** das komplette Production Stack (Nginx, Certbot, etc.).

---

## 🚀 Quick Start (3 Steps)

### 1. PostgreSQL starten (nur die DB)

```bash
docker compose -f docker-compose.dev.yml up -d
```

Das startet **nur** PostgreSQL auf `localhost:5432` mit:
- User: `evmonitor`
- Password: `evmonitor`
- Database: `ev_monitor`

### 2. Backend starten

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Das läuft auf `http://localhost:8080` mit:
- **Dev Profile aktiv** (SQL logging, Dev JWT Secret)
- **DevDataSeeder aktiv** (erstellt Test-Users + Cars + Charging Logs automatisch)
- **CORS für localhost:5173** (Vue dev server)

**Test Users (nach Seeder):**
- Email: `test1@ev-monitor.net` / `test2@ev-monitor.net` / `test3@ev-monitor.net`
- Password: `Test1234!`

### 3. Frontend starten (in neuem Terminal)

```bash
cd frontend
npm run dev
```

Das läuft auf `http://localhost:5173` (oder 5174 wenn Port belegt).

---

## 🗄️ Database Management

### Fresh Start (Drop alle Tables)

```sql
-- In IntelliJ Database Console:
DROP TABLE IF EXISTS ev_log CASCADE;
DROP TABLE IF EXISTS car CASCADE;
DROP TABLE IF EXISTS coin_log CASCADE;
DROP TABLE IF EXISTS vehicle_specification CASCADE;
DROP TABLE IF EXISTS users CASCADE;
```

Nach dem Drop erstellt Hibernate beim nächsten Backend-Start alle Tables neu, und der **DevDataSeeder** befüllt sie automatisch.

### Database anschauen

**IntelliJ Database Tool:**
- Host: `localhost`
- Port: `5432`
- User: `evmonitor`
- Password: `evmonitor`
- Database: `ev_monitor`

---

## 🧪 API Testing

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test1@ev-monitor.net","password":"Test1234!"}'

# Response: {"token":"eyJhbG..."}

# Get Cars
curl -H "Authorization: Bearer {TOKEN}" http://localhost:8080/api/cars

# Get Charging Logs
curl -H "Authorization: Bearer {TOKEN}" http://localhost:8080/api/logs

# Get Statistics (replace {CAR_ID} with real UUID from /api/cars)
curl -H "Authorization: Bearer {TOKEN}" \
  'http://localhost:8080/api/logs/statistics?carId={CAR_ID}'
```

---

## 🛑 Stoppen

```bash
# PostgreSQL stoppen
docker compose -f docker-compose.dev.yml down

# Backend: Ctrl+C im Terminal
# Frontend: Ctrl+C im Terminal
```

---

## ⚙️ Configuration

**Backend Config:** `backend/src/main/resources/application-dev.yml`
- JWT Secret: `dev-secret-key-CHANGE-IN-PRODUCTION-...`
- SQL Logging: `true`
- Database: `localhost:5432/ev_monitor`

**CORS:** Automatisch für `localhost:5173, localhost:5174` erlaubt.

---

## ❌ Troubleshooting

### Problem: Backend startet nicht - "JWT_SECRET required"

**Lösung:** Nutze das `dev` Profile:
```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### Problem: Database connection refused

**Lösung:** PostgreSQL Container prüfen:
```bash
docker compose -f docker-compose.dev.yml ps
docker compose -f docker-compose.dev.yml logs db
```

### Problem: CORS Error im Frontend

**Lösung:** Backend muss laufen auf Port 8080, Frontend auf 5173 (oder 5174).
SecurityConfig erlaubt bereits beide Ports.

### Problem: "Table does not exist"

**Lösung:** Drop Tables (siehe oben), dann Backend neu starten.

---

## 📦 Production vs Dev

**Production (`docker-compose.yml`):**
- Nutzt `.env` file mit Production Secrets
- Nginx Reverse Proxy + SSL
- Certbot für Let's Encrypt
- Profile: `prod`

**Local Dev (`docker-compose.dev.yml`):**
- Keine Secrets nötig (Dev defaults)
- Keine Nginx, kein SSL
- Backend + Frontend direkt per `./gradlew` und `npm run dev`
- Profile: `dev`
