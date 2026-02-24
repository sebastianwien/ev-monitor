# 🚀 Quick Commands Reference

Schnelle Übersicht für die wichtigsten Commands.

---

## ⚡ One-Liner Starts

```bash
# Production-Like (Nginx Port 80) - EMPFOHLEN FÜR TESTING
docker compose -f docker-compose.local.yml up --build

# Nur Frontend Hot Reload (Backend muss schon laufen)
docker compose -f docker-compose.frontend-dev.yml up

# Schnellster lokaler Start mit Script
./dev.sh
```

---

## 🛑 Stops

```bash
# Docker Setups
docker compose -f docker-compose.local.yml down
docker compose -f docker-compose.frontend-dev.yml down

# Script
./stop-dev.sh
# oder Ctrl+C
```

---

## 🔄 Fresh Start (alles löschen und neu)

```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up --build
```

---

## 📊 Database

```bash
# Connect to DB
docker compose -f docker-compose.local.yml exec db psql -U evmonitor -d ev_monitor

# Drop all tables (for fresh start)
docker compose -f docker-compose.local.yml exec -T db psql -U evmonitor -d ev_monitor <<EOF
DROP TABLE IF EXISTS ev_log CASCADE;
DROP TABLE IF EXISTS car CASCADE;
DROP TABLE IF EXISTS coin_log CASCADE;
DROP TABLE IF EXISTS vehicle_specification CASCADE;
DROP TABLE IF EXISTS users CASCADE;
EOF
```

---

## 🔍 Logs

```bash
# Docker
docker compose -f docker-compose.local.yml logs -f backend
docker compose -f docker-compose.local.yml logs -f nginx

# Script
tail -f logs/backend.log
tail -f logs/frontend.log
```

---

## 🧪 Quick API Test

```bash
# Login (via Nginx Port 80)
curl -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test1@ev-monitor.net","password":"Test1234!"}'

# Get Cars (replace {TOKEN})
curl -H "Authorization: Bearer {TOKEN}" http://localhost/api/cars

# Get Statistics (replace {TOKEN} and {CAR_ID})
curl -H "Authorization: Bearer {TOKEN}" \
  'http://localhost/api/logs/statistics?carId={CAR_ID}'
```

---

## 🏗️ Builds

```bash
# Rebuild Frontend only (if using Port 80 setup)
docker compose -f docker-compose.local.yml up --build nginx

# Rebuild Backend only
docker compose -f docker-compose.local.yml up --build backend

# Rebuild everything
docker compose -f docker-compose.local.yml up --build
```

---

## 📦 URLs After Start

| Setup | Frontend | Backend | Database |
|-------|----------|---------|----------|
| docker-compose.local.yml | http://localhost | http://localhost:8080 | localhost:5432 |
| docker-compose.frontend-dev.yml | http://localhost:5173 | (must be running) | (must be running) |
| dev.sh | http://localhost:5173 | http://localhost:8080 | localhost:5432 |

---

## 🔑 Test Users

```
test1@ev-monitor.net / Test1234!
test2@ev-monitor.net / Test1234!
test3@ev-monitor.net / Test1234!
```

Each user has:
- 2 cars
- ~60-80 charging logs per car
- Data spanning 1 year

---

## 🆘 Common Issues

### "Port already in use"
```bash
lsof -i :80    # or :8080, :5173, :5432
kill -9 <PID>
```

### "Cannot connect to backend"
```bash
docker compose -f docker-compose.local.yml ps
docker compose -f docker-compose.local.yml logs backend
```

### "No test users found"
Drop users table and restart - DevDataSeeder will recreate them.

---

## 📖 Full Docs

- **Complete Guide:** `DEV_QUICK_START.md`
- **Production:** `DEPLOYMENT_CHECKLIST.md`
