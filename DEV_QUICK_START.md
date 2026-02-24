# 🚀 Local Development - Quick Start

Du hast **drei Optionen** für lokales Dev. Wähle die, die zu deinem Workflow passt:

---

## Option 1: Production-Like (Docker + Nginx auf Port 80) 🎯 **EMPFOHLEN**

**Für:** Realitätsnahes Testing, kein CORS, Production-Setup simulieren

### Start:
```bash
docker compose -f docker-compose.local.yml up --build
```

**Das war's!** Nach ~2-3 Minuten läuft alles:
- ✅ PostgreSQL (auto-creates tables)
- ✅ Backend (mit DevDataSeeder - erstellt 3 Users + 6 Cars + 370 Charging Logs)
- ✅ Frontend (gebuildete statische Files)
- ✅ Nginx (serviert Frontend + proxied Backend)

**Access:**
- **Application: http://localhost** (Port 80) ← Nutze das!
- Backend API direkt: http://localhost:8080 (für API Testing)
- Database: localhost:5432

**Vorteile:**
- ✅ Simuliert Production Setup perfekt
- ✅ Kein CORS (alles über Port 80)
- ✅ Frontend ist gebaut (wie in Production)
- ✅ Nginx Proxy wie in Production

**Nachteile:**
- ❌ Kein Hot Reload (Frontend muss neu gebaut werden bei Changes)
- ❌ Langsamer Build (~2-3min)

**Stop:**
```bash
docker compose -f docker-compose.local.yml down
```

**Fresh Start (drop tables):**
```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up --build
```

---

## Option 2: Frontend Hot Reload (für aktive Frontend-Entwicklung) 🔥

**Für:** Schnelles Frontend Development mit Vite Hot Reload

### Start:
```bash
# Terminal 1: Start Backend + DB + Nginx
docker compose -f docker-compose.local.yml up --build

# Terminal 2: Start Frontend Dev Server (in neuem Terminal)
docker compose -f docker-compose.frontend-dev.yml up
```

**Access:**
- **Frontend Dev Server: http://localhost:5173** (mit Hot Reload!)
- Backend API: http://localhost:8080
- Production-like: http://localhost (Nginx)

**Vorteile:**
- ✅ Instant Hot Reload bei Frontend Changes
- ✅ Backend + DB laufen stabil
- ✅ Beide URLs funktionieren parallel

**Nachteile:**
- ❌ Zwei Terminals nötig
- ❌ Port 5173 hat CORS (aber funktioniert)

**Stop:**
```bash
# Terminal 2
docker compose -f docker-compose.frontend-dev.yml down

# Terminal 1
docker compose -f docker-compose.local.yml down
```

---

## Option 3: Dev Script (komplett lokal, max Performance) ⚡

**Für:** Maximale Performance, lokales Java/Node, natives Hot Reload

### Start:
```bash
./dev.sh
```

**Das Script:**
1. ✅ Startet PostgreSQL (Docker)
2. ✅ Fragt, ob Tables gedropped werden sollen (optional)
3. ✅ Startet Backend (Spring Boot mit DevDataSeeder)
4. ✅ Startet Frontend (Vite dev server)
5. ✅ Zeigt Logs in Console

**Access:**
- Frontend: http://localhost:5173
- Backend: http://localhost:8080

**Vorteile:**
- ✅ Schneller Start (~30 Sekunden)
- ✅ Natives Hot Reload (super schnell)
- ✅ Beste Performance für Development

**Nachteile:**
- ❌ Braucht lokales Java 21 + Node 20
- ❌ Kein Nginx (CORS könnte theoretisch Problem sein, funktioniert aber)

**Stop:**
```bash
./stop-dev.sh
```
oder einfach `Ctrl+C` im Terminal.

---

## 🎯 Empfehlung nach Use Case:

| Use Case | Empfohlene Option |
|----------|-------------------|
| **Testing wie in Production** | Option 1 (Nginx Port 80) |
| **Frontend Development** | Option 2 (Hot Reload) |
| **Backend Development** | Option 1 oder 3 |
| **Full Stack Development** | Option 2 (beide parallel) |
| **Demo für andere** | Option 1 (Production-like) |

---

## 🧪 Test Users (Auto-Created)

Nach dem Start existieren automatisch:

| Email | Password | Cars | Logs |
|-------|----------|------|------|
| test1@ev-monitor.net | Test1234! | 2 | ~130 |
| test2@ev-monitor.net | Test1234! | 2 | ~130 |
| test3@ev-monitor.net | Test1234! | 2 | ~110 |

**Total: 6 Cars, ~370 Charging Logs (verteilt über 1 Jahr)**

---

## 📊 API Testing

### Via Nginx (Port 80) - wie in Production:
```bash
# Login
curl -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test1@ev-monitor.net","password":"Test1234!"}'

# Get Cars
curl -H "Authorization: Bearer {TOKEN}" http://localhost/api/cars

# Get Statistics
curl -H "Authorization: Bearer {TOKEN}" \
  'http://localhost/api/logs/statistics?carId={CAR_ID}'
```

### Direkt an Backend (Port 8080):
```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test1@ev-monitor.net","password":"Test1234!"}'
```

---

## 🗄️ Database Access

**Connection Info:**
- Host: `localhost`
- Port: `5432`
- User: `evmonitor`
- Password: `evmonitor`
- Database: `ev_monitor`

**IntelliJ Database Tool:**
1. View → Tool Windows → Database
2. + → Data Source → PostgreSQL
3. Enter connection info above

**Manual SQL:**
```bash
# Option 1 & 2:
docker compose -f docker-compose.local.yml exec db psql -U evmonitor -d ev_monitor

# Option 3:
docker compose -f docker-compose.dev.yml exec db psql -U evmonitor -d ev_monitor
```

---

## 🔄 Frontend Rebuild (Option 1)

Wenn du Frontend Code änderst und über Port 80 testen willst:
```bash
# Nginx neu bauen (beinhaltet Frontend Build)
docker compose -f docker-compose.local.yml up --build nginx
```

Oder nutze **Option 2** für Hot Reload während Development!

---

## ❌ Troubleshooting

### Port 80 already in use
```bash
# Check what's using it
lsof -i :80

# Often it's Apache
sudo apachectl stop
```

### Port 5173 already in use
```bash
lsof -i :5173
kill -9 <PID>
```

### Backend doesn't start
```bash
# Option 1 & 2:
docker compose -f docker-compose.local.yml logs backend

# Option 3:
cat logs/backend.log
```

### Nginx can't connect to backend
```bash
# Make sure backend is running
docker compose -f docker-compose.local.yml ps

# Check backend logs
docker compose -f docker-compose.local.yml logs backend
```

### DevDataSeeder doesn't run
Seeder only runs if `test1@ev-monitor.net` doesn't exist. Drop users table to trigger re-seed.

---

## 🔄 Fresh Start (Reset Everything)

### Option 1 & 2:
```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up --build
```

### Option 3:
```bash
./dev.sh
# Answer "y" when asked to drop tables
```

---

## 📁 Was ist wo?

```
ev-monitor/
├── docker-compose.local.yml        ← Production-like (Nginx Port 80)
├── docker-compose.frontend-dev.yml ← Frontend Hot Reload
├── docker-compose.dev.yml          ← Nur PostgreSQL (für Script)
├── dev.sh                          ← Dev Script (Auto-Start)
├── nginx/
│   ├── Dockerfile.local           ← Multi-stage (Frontend Build + Nginx)
│   └── conf.d/
│       └── app.conf.local         ← Nginx Config ohne SSL
└── backend/src/main/resources/
    └── application-dev.yml        ← Dev Config (JWT Secret, SQL Logging)
```

---

## 💡 Development Workflow Beispiele

### Scenario 1: "Ich will nur schnell was testen"
```bash
docker compose -f docker-compose.local.yml up
# → http://localhost
```

### Scenario 2: "Ich entwickle am Frontend"
```bash
# Terminal 1
docker compose -f docker-compose.local.yml up

# Terminal 2
docker compose -f docker-compose.frontend-dev.yml up
# → http://localhost:5173 (Hot Reload!)
```

### Scenario 3: "Ich entwickle am Backend"
```bash
# Option A: Docker
docker compose -f docker-compose.local.yml up
# Backend Code ändern → Container neu starten

# Option B: Lokal (schneller)
./dev.sh
# Backend Code ändern → Spring Boot DevTools restart automatisch
```

### Scenario 4: "Ich will testen wie es in Production aussieht"
```bash
docker compose -f docker-compose.local.yml up --build
# → http://localhost (Port 80, wie Production!)
```

---

## 📚 More Docs

- **Production Deployment:** `DEPLOYMENT_CHECKLIST.md`
- **Server Setup:** `SERVER_SETUP.md`
- **Old Manual Setup:** `LOCAL_DEV_SETUP.md` (deprecated, nutze lieber dieses hier)
