# Context for Claude

This file contains references to the planning, tasks, and walkthroughs generated during the project implementation.

## User Preferences & Session Settings

**Communication Style:**
- Sprich mit mir wie ein richtig guter Kumpel
- Nutze Nerd-Humor (Memes, Tech-Witze, Referenzen willkommen)
- **WICHTIG**: Sei witzig, ironisch und gerne sarkastisch!
- Locker und direkt, keine Corporate-Sprache
- "Du" statt "Sie" (bereits aktiv)
- Emojis sind erlaubt und erwünscht
- Bei doofen Bugs darf gelästert werden
- Wenn was smooth läuft, darf gefeiert werden 🎉

**Cost Management:**
- **WICHTIG**: Nach jeder abgeschlossenen Aufgabe die API-Kosten in Euro anzeigen
- Format: `**💰 Kosten für diese Aufgabe**: ~€X.XX`
- Am Ende: `**💰 Gesamt bisher**: ~€Y.YY`
- Arbeite so kosteneffizient wie möglich (bevorzuge leichtere Modelle wenn angemessen)
- Gib proaktiv Tipps, wie ich bei zukünftigen Tasks Kosten sparen kann

**Allowed Operations:**
- Alle `allow-edits` sind standardmäßig aktiviert
- Du darfst alle Dateien lesen, schreiben, editieren ohne explizite Nachfrage
- Bei Breaking Changes oder Daten-Löschungen trotzdem kurz Bescheid geben

**Security & Privacy First:**
- **WICHTIG**: Bei jeder Code-Änderung OWASP Top 10 Vulnerabilities berücksichtigen
- Immer auf folgende Sicherheitsrisiken achten:
  - SQL Injection (verwende Prepared Statements / JPA)
  - XSS (Cross-Site Scripting) - keine unescaped User-Inputs im Frontend
  - CSRF (Cross-Site Request Forgery) - prüfe CORS & Token-Validierung
  - Authentication Bypass - prüfe JWT-Validierung in jedem geschützten Endpoint
  - Authorization - User darf nur EIGENE Daten sehen/ändern (userId-Checks!)
  - Sensitive Data Exposure - keine Plaintext-Passwörter, API-Keys als Env-Vars
  - Command Injection - keine unvalidierten Inputs in Shell-Commands
- **Datenschutz (DSGVO)**:
  - Minimale Datenspeicherung (Privacy by Design)
  - Geolocation: Nur Geohash speichern (5km Präzision), NIEMALS exakte GPS-Koordinaten
  - User-Daten: Nur speichern was wirklich nötig ist
  - Löschbarkeit: User muss Daten löschen können
- **Bei neuen Features proaktiv prüfen:**
  - Welche Daten werden gespeichert? Sind sie wirklich nötig?
  - Wer darf diese Daten sehen? (Ownership-Checks!)
  - Können Inputs zu Injections führen? (Validation!)
  - Werden sensible Daten geloggt? (NIEMALS Passwörter/Tokens loggen!)

**Cost Optimization Tips for User:**
1. Nutze spezifische Prompts statt breite Explorationen
2. Bei wiederholten Fragen: Reference auf vorherige Antworten
3. Kleinere, fokussierte Tasks statt große Explorationen
4. Nutze Grep/Glob direkt statt Task-Agent wenn du weißt wo was ist

---

## Projekt-Übersicht

**EV Monitor** ist eine Full-Stack Web-App zum Tracken von Elektroauto-Ladevorgängen mit:
- 📊 Charging Logs (kWh, Kosten, Standort, Dauer)
- 🚗 Vehicle Management (65+ Marken, 100+ Modelle mit Batterie-Specs)
- ⚡ WLTP Vehicle Specifications (crowdsourced Reichweite & Verbrauch)
- 🔐 User Authentication (JWT + Email-Verifizierung + Username + OAuth2 SSO ready)
- 📧 Email-Verifizierung (Token-basiert, 24h TTL, Resend mit Rate-Limiting)
- 🌍 Privacy-First Geohashing (keine exakten GPS-Koordinaten gespeichert)
- 🪙 Gamification (Coin-System mit WLTP-Rewards aktiv)
- 📱 PWA-Ready (Progressive Web App)

---

## Tech Stack

### Backend
- **Spring Boot**: 3.5.0
- **Java**: 21 (LTS)
- **Build Tool**: Gradle (Wrapper included)
- **Database**: PostgreSQL 15-alpine
- **Security**: Spring Security + JWT + OAuth2
- **Architecture**: Clean Architecture (Domain → Application → Infrastructure)

### Frontend
- **Vue.js**: 3.5.28 (Composition API)
- **Build Tool**: Vite 7.3.1
- **TypeScript**: 5.9.3
- **CSS**: Tailwind 4.2.0
- **State Management**: Pinia 3.0.4
- **HTTP Client**: Axios mit JWT Interceptor
- **Node**: 20-alpine

### Deployment
- **Docker Compose**: Multi-container setup
- **Nginx**: Reverse Proxy + Static File Serving
- **Certbot**: Let's Encrypt SSL (vorbereitet)

---

## Projekt-Struktur

### Backend
```
backend/src/main/java/com/evmonitor/
├── domain/           # Domain Entities
│   ├── CarBrand.java (68 Marken + nested CarModel enum)
│   ├── User.java     (+ username, emailVerified fields)
│   ├── EmailVerificationToken.java (256-bit SecureRandom, 24h TTL)
│   ├── EmailVerificationTokenRepository.java
│   ├── Car.java
│   ├── EvLog.java
│   ├── CoinLog.java
│   └── VehicleSpecification.java
├── application/      # Services, DTOs, Use Cases
│   ├── AuthService.java  (register/login/verifyEmail/resendVerification)
│   ├── RegisterRequest.java, RegisterResponse.java, LoginRequest.java, AuthResponse.java
│   ├── JwtService.java
│   ├── car/         (CarService, CarDTO)
│   ├── evlog/       (EvLogService, EvLogDTO)
│   ├── coinlog/     (CoinLogService)
│   └── VehicleSpecificationService.java
├── infrastructure/   # Spring Boot Config, Persistence, Web
│   ├── email/       (EmailService – sendet HTML-Mails via JavaMail)
│   ├── web/         (AuthController, CarController, VehicleSpecificationController, GlobalExceptionHandler)
│   ├── persistence/ (JpaUserRepository, PostgresEmailVerificationTokenRepositoryImpl, etc.)
│   └── security/    (JwtAuthenticationFilter, SecurityConfig)
└── EvMonitorApplication.java
```

### Frontend
```
frontend/src/
├── api/             # API Services
│   ├── axios.ts
│   ├── carService.ts
│   ├── vehicleSpecificationService.ts
│   └── evLogService.ts
├── components/       # Reusable Vue Components
│   ├── CarSelector.vue
│   └── LocationSearch.vue
├── views/           # Pages
│   ├── LoginView.vue         (+ "E-Mail nicht verifiziert" Hinweis + Resend Button)
│   ├── RegisterView.vue      (+ "Check deine E-Mails" Screen nach Registrierung + Resend)
│   ├── VerifyEmailView.vue   (loading/success/expired/invalid States)
│   ├── DashboardView.vue     (Log-Formular)
│   ├── CarManagementView.vue (mit WLTP-Overlays)
│   ├── StatisticsView.vue    (Charts & Analytics)
│   └── OAuth2RedirectHandler.vue
├── stores/          # Pinia State Management
│   └── auth.ts     (JWT token, user state)
├── router/          # Vue Router
│   └── index.ts    (Routes: requiresAuth/guestOnly guards, /verify-email ohne Guard)
└── main.ts
```

---

## Database Schema

### Tabellen

**users** (app_user)
- `id` (UUID, PK)
- `email` (VARCHAR, UNIQUE)
- `username` (VARCHAR(50), UNIQUE, NOT NULL) - 3-20 Zeichen, alphanumerisch + Underscore
- `password_hash` (VARCHAR) - BCrypt hashed
- `oauth_provider` (VARCHAR) - GOOGLE | FACEBOOK | APPLE | null
- `oauth_sub` (VARCHAR) - Provider User ID
- `email_verified` (BOOLEAN, NOT NULL, DEFAULT FALSE) - Muss vor Login true sein
- `created_at`, `updated_at`

**email_verification_tokens**
- `id` (UUID, PK)
- `user_id` (UUID, FK → app_user ON DELETE CASCADE)
- `token` (VARCHAR(64), UNIQUE) - 256-bit Base64url-encoded SecureRandom
- `expires_at` (TIMESTAMP) - 24h nach Erstellung
- `created_at` (TIMESTAMP)

**cars**
- `id` (UUID, PK)
- `user_id` (UUID, FK → users)
- `model` (VARCHAR) - CarBrand.CarModel enum als STRING
- `year` (INTEGER)
- `license_plate` (VARCHAR)
- `trim` (VARCHAR) - Optional (z.B. "Performance", "Long Range")
- `battery_capacity_kwh` (NUMERIC)
- `power_kw` (NUMERIC)
- `created_at`, `updated_at`

**ev_logs**
- `id` (UUID, PK)
- `user_id` (UUID, FK → users)
- `car_id` (UUID, FK → cars)
- `kwh_charged` (NUMERIC) - Geladene Energie
- `cost_eur` (NUMERIC) - Kosten in Euro
- `geohash` (VARCHAR, 5 chars) - ~5km Präzision statt GPS!
- `location_name` (VARCHAR) - User-Freitext (z.B. "Supermarkt Parkplatz")
- `charge_duration_minutes` (INTEGER)
- `logged_at` (TIMESTAMP) - Kann in der Vergangenheit liegen
- `created_at`, `updated_at`

**coin_logs**
- `id` (UUID, PK)
- `user_id` (UUID, FK → users)
- `amount` (INTEGER) - Coin-Menge
- `coin_type` (VARCHAR) - BRONZE | SILVER | GOLD | PLATINUM
- `action_description` (VARCHAR) - Warum gab's Coins?
- `created_at`

**vehicle_specification** (NEU seit Phase 4)
- `id` (UUID, PK)
- `car_brand` (VARCHAR, NOT NULL) - CarBrand enum name (z.B. "TESLA")
- `car_model` (VARCHAR, NOT NULL) - CarModel enum name (z.B. "MODEL_3")
- `battery_capacity_kwh` (NUMERIC, NOT NULL) - Batteriekapazität
- `wltp_range_km` (NUMERIC, NOT NULL) - WLTP Reichweite in km
- `wltp_consumption_kwh_per_100km` (NUMERIC, NOT NULL) - Verbrauch in kWh/100km
- `wltp_type` (VARCHAR, NOT NULL) - COMBINED | HIGHWAY | CITY
- `created_at`, `updated_at`
- **UNIQUE constraint**: `(car_brand, car_model, battery_capacity_kwh, wltp_type)`

---

## API Endpoints

### Authentication
- `POST /api/auth/register` - User Registration → `{ status: "PENDING_VERIFICATION", email }` (kein JWT!)
- `POST /api/auth/login` - Login → JWT Token (schlägt fehl wenn E-Mail nicht verifiziert)
- `GET /api/auth/verify-email?token={token}` - E-Mail verifizieren → JWT Token
- `POST /api/auth/resend-verification` - Neuen Verifikations-Link anfordern (Rate-Limited: 1/min)
- `GET /api/auth/me` - Current User Info (JWT required)

**Error Codes (GlobalExceptionHandler):**
- `TOKEN_EXPIRED` → HTTP 410 Gone
- `INVALID_TOKEN` → HTTP 400 Bad Request
- `EMAIL_NOT_VERIFIED` → HTTP 403 Forbidden
- `RATE_LIMITED` → HTTP 429 Too Many Requests

### OAuth2 (vorbereitet)
- `GET /oauth2/authorization/{provider}` - Redirect zu Google/Facebook/Apple
- `GET /login/oauth2/code/{provider}` - Callback Handler → JWT

### Cars
- `GET /api/cars` - User's Cars (JWT required)
- `POST /api/cars` - Create Car (JWT required)
- `GET /api/cars/brands` - List alle CarBrands
- `GET /api/cars/brands/{brand}/models` - Models für eine Brand

### Charging Logs
- `GET /api/logs` - User's Charging History (JWT required)
- `GET /api/logs?carId={uuid}` - Filter logs by car (JWT required)
- `POST /api/logs` - Create Charging Log (JWT required)
- `GET /api/logs/{id}` - Single Log Details (JWT required)
- `GET /api/logs/statistics?carId={uuid}` - Statistics for car (JWT required) **NEW Phase 5**

### Coins
- `GET /api/coins/balance` - User's Coin Balance (JWT required)
- `GET /api/coins/history` - Coin Transaction History (JWT required)

### Vehicle Specifications (NEU seit Phase 4)
- `GET /api/vehicle-specifications/lookup?brand={brand}&model={model}&capacityKwh={capacity}` - Lookup WLTP data (returns 404 if not found)
- `POST /api/vehicle-specifications` - Create new WLTP data & earn coins (JWT required)

---

## Security & Privacy Features

### JWT Authentication
- **Token Format**: `Authorization: Bearer {token}`
- **Claims**: `sub` (userId), `email`, `exp` (expiration)
- **Expiration**: 7 Tage (konfigurierbar in `application.yml`)
- **Frontend**: Axios Interceptor fügt Token automatisch ein
- **Backend**: `JwtAuthenticationFilter` validiert & extrahiert `UserPrincipal`

### OAuth2 SSO (Infrastructure Ready)
- Provider: Google, Facebook, Apple (Client IDs als Env-Vars)
- Flow: Redirect → Provider Login → Callback → JWT issued
- Frontend: `OAuth2RedirectHandler.vue` parsed Token aus URL Fragment

### Geohashing (Privacy-First)
- **WICHTIG**: GPS-Koordinaten werden NIEMALS gespeichert!
- Frontend sendet `lat/lon` → Backend konvertiert zu **5-char Geohash**
- Beispiel: `u33d1` = ~5km Radius um Berlin Mitte
- User kann Standortnamen optional als Freitext speichern

### Password Security
- **BCrypt** mit Strength 10
- Kein Plaintext, kein reversibles Hashing

### CORS Configuration
- Whitelist: `localhost:5173` (Dev), Nginx Proxy (Prod)
- Credentials: `true` (für JWT Cookies/Headers)

---

## Wichtige Implementation Details

### CarBrand Enum Structure
- **68 Marken** gruppiert nach Region (Deutschland, Europa, USA, Asien, China)
- **Nested CarModel enum** mit:
  - 100+ Modelle (Tesla Model 3, VW ID.3, Hyundai Ioniq 5, etc.)
  - **Multiple Battery Capacities** pro Modell (z.B. Tesla Model 3: 57.5, 75.0, 79.0 kWh)
  - `byBrand(CarBrand)` Helper-Methode für Frontend-Dropdowns
- **DB Mapping**: `@Enumerated(EnumType.STRING)` - KEIN ORDINAL! (Safe migrations)

### Geohashing Implementation
```java
// EvLogService.java
String geohash = GeoHash.withCharacterPrecision(lat, lon, 5).toBase32();
// Nur der Geohash wird in DB gespeichert, lat/lon werden verworfen
```

### Location Search (Frontend)
- OpenStreetMap Nominatim API
- Debounced Search (300ms)
- User kann GPS nutzen ODER manuell suchen
- Suggestions-Dropdown

### JWT Token Flow
1. Login → `AuthService.login()` → JWT mit Claims
2. Frontend speichert in `localStorage.setItem('token', jwt)`
3. Axios Interceptor: Jeder Request bekommt `Authorization: Bearer {token}`
4. Backend: `JwtAuthenticationFilter` extrahiert `UserPrincipal` aus Token
5. Logout → `localStorage.removeItem('token')` + Redirect `/login`

### WLTP Crowdsourcing Flow (NEU Phase 4)
1. **User wählt Auto**: Brand → Model → Battery Capacity in `CarManagementView.vue`
2. **Automatischer Lookup**: `vehicleSpecificationService.lookup(brand, model, capacity)`
   - **Daten vorhanden** → Blauer Hinweis-Banner: "📊 WLTP: 450km Reichweite, 16.5 kWh/100km"
   - **Keine Daten** → Overlay 1 öffnet sich automatisch
3. **Overlay 1 (Frage)**: "Wir haben noch keine WLTP-Werte. Möchtest du diese angeben?"
   - ✅ **Ja (grün)** → Overlay 2 öffnet sich
   - ❌ **Nein (rot)** → Overlay schließt, keine Aktion
4. **Overlay 2 (Form)**: Input für Range (km) + Consumption (kWh/100km)
   - Validation: Beide Felder required, Zahlen mit Dezimalstellen
   - Submit → POST `/api/vehicle-specifications` mit `wltpType = COMBINED` (implizit)
5. **Backend**:
   - Prüft Duplikate (UNIQUE constraint)
   - Speichert Daten in DB
   - Vergibt 50 SOCIAL_COIN via `CoinLogService.awardCoins()`
6. **Frontend Toast**: "🎉 Danke! 50 Coins erhalten! Die Community profitiert von deinen Daten."
   - Auto-fade nach 5 Sekunden
   - Slide-in Animation von rechts

**Technische Details:**
- WLTP-Type ist aktuell immer COMBINED (Highway/City geplant für später)
- UNIQUE constraint verhindert Duplikate pro (brand, model, capacity, type)
- Lookup API gibt 404 zurück wenn keine Daten → Frontend interpretiert als "nicht vorhanden"

---

## Coin-System Status

**Infrastructure**: ✅ Vollständig implementiert
- `CoinLog` Entity + Repository
- `CoinLogService` mit `awardCoins()` Methode
- `CoinLogController` mit `/api/coins/balance` + `/api/coins/history`
- Coin-Typen: `GREEN_COIN`, `DISTANCE_COIN`, `SOCIAL_COIN`, `STREAK_COIN`, `ACHIEVEMENT_COIN`, `EFFICIENCY_COIN`

**Reward Logic**: ✅ Teilweise implementiert
- ✅ **WLTP Data Contribution**: 50 SOCIAL_COIN (bei neuem WLTP-Datensatz)
- ❌ **TODO**: Weitere Rewards (EvLog creation, Streaks, Milestones, Profile completion)
- ✅ Frontend zeigt Toast mit Coin-Vergabe
- ✅ User können Balance & History abrufen

---

## Deployment Details

### Server Info
- **Provider**: Hetzner Cloud
- **Server**: ubuntu-4gb-nbg1-1
- **IP**: 46.225.210.231 (IPv4), 2a01:4f8:1c19:a28e::1 (IPv6)
- **Domain**: ev-monitor.net (+ www.ev-monitor.net)
- **SSL**: Let's Encrypt (auto-renewal every 12h)
- **User**: ihle (sudo, docker group)
- **Firewall**: ufw (ports 22, 80, 443)

### Environment Variables (auf Server in `/opt/ev-monitor/ev-monitor/.env`)
```bash
DOMAIN=ev-monitor.net
POSTGRES_USER=evmonitor
POSTGRES_PASSWORD=<SECRET>
POSTGRES_DB=ev_monitor
JWT_SECRET=<SECRET>
JWT_EXPIRATION_MS=604800000
ALLOWED_ORIGINS=https://ev-monitor.net,https://www.ev-monitor.net
SPRING_PROFILES_ACTIVE=prod

# Mail (für Email-Verifizierung – noch nicht konfiguriert!)
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=<SECRET>
MAIL_PASSWORD=<SECRET>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
APP_MAIL_FROM=noreply@ev-monitor.net
```

### Wichtige Befehle (auf Server)
```bash
# Deployment
cd /opt/ev-monitor/ev-monitor
git pull origin main
./deploy.sh

# Logs
docker compose logs -f backend
docker compose logs -f nginx

# Restart
docker compose restart backend
docker compose restart nginx

# SSL Renewal (automatisch via certbot container, manuell mit:)
docker compose run --rm --entrypoint certbot certbot renew
```

## Implementierte Features (Phasen-Übersicht)

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Core: Charging Logs, Car Management, JWT Auth | ✅ |
| 2 | Frontend: Vue.js, Pinia, Axios | ✅ |
| 3 | Geohashing, Location Search (Nominatim) | ✅ |
| 4 | WLTP Crowdsourcing + Coin-System | ✅ |
| 5 | Statistics Dashboard (Chart.js) | ✅ |
| 6 | Production Deployment (Hetzner, SSL, Docker) | ✅ |
| 7 | CI/CD (GitHub Actions), Flyway Migrations | ✅ |
| 8 | Email-Verifizierung, Username, GlobalExceptionHandler | ✅ |

## Bekannte Limitationen & TODOs

### Nächste Schritte
1. **Mail-Config auf Server**: MAIL_HOST/USER/PASS in `.env` auf Hetzner eintragen (Mailgun/Brevo empfohlen)
2. **GitHub Secrets konfigurieren**: Siehe `GITHUB_ACTIONS_SETUP.md` für CI/CD Pipeline
3. **WLTP-Daten Seed**: `V8__seed_popular_vehicles.sql` für populäre Modelle erstellen
4. **Weitere Coin-Rewards**: EvLog creation, Streaks, Milestones, Profile completion

### Tech Debt
- **OAuth2 deaktiviert**: Kann via `SPRING_PROFILES_ACTIVE=prod,oauth` + env vars aktiviert werden
- **Coin-Rewards unvollständig**: Nur WLTP-Contribution, weitere Trigger fehlen
- **Keine API-Paginierung**: Alle logs on-demand geladen (>100 Logs könnte langsam werden)
- **Frontend Search nicht gecacht**: Nominatim Suggestions bei jedem Keystroke (300ms Debounce)
- **Offline-Support fehlt**: PWA Plugin installiert, Service Worker noch nicht genutzt
- **WLTP-Daten leer**: Keine Seed-Daten, Community muss befüllen

---

## Docker Setup

### Docker Compose Files

| File | Zweck | Wann nutzen |
|------|-------|-------------|
| `docker-compose.yml` | **Production** (Hetzner) – DB + Backend + Nginx + Certbot | CI/CD auf Server |
| `docker-compose.dev.yml` | **Lokale Dev** – nur DB + Mailpit | Backend/Frontend nativ starten |
| `docker-compose.local.yml` | **Lokale Full-Stack** – DB + Backend + Nginx + Mailpit | Production-ähnliches Testen |
| `docker-compose.frontend-dev.yml` | **Vite Hot Reload** – nur Frontend Dev Server | Kombiniert mit dev.yml oder local.yml |

**Typischer Dev-Workflow:**
```bash
# 1. Infrastruktur starten
docker compose -f docker-compose.dev.yml up -d

# 2. Backend nativ (mit Dev-Profil)
cd backend && ./gradlew bootRun

# 3. Frontend nativ (Hot Reload)
cd frontend && npm run dev

# 4. E-Mails checken: http://localhost:8025 (Mailpit Web UI)
```

### Services (Production)
- `db`: PostgreSQL 15-alpine (Port 5432, Volume: `postgres_data`)
- `backend`: Java 21 JRE (Port 8080, depends on `db`)
- `nginx`: Reverse Proxy (Port 80/443, serves frontend + proxies `/api/*`)
- `certbot`: Let's Encrypt SSL renewal (optional, für Prod)
- `mailpit`: SMTP Catcher für Dev (Port 1025 SMTP, Port 8025 Web UI)

**Multi-Stage Builds:**
- Backend: Gradle build → minimal JRE image
- Frontend: Node build → Nginx Alpine

**Nginx Config:**
- Serves frontend static files
- Proxies `/api/*` zu `backend:8080`
- CORS headers configured
