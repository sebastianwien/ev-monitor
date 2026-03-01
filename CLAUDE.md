# Context for Claude

This file is the central overview for the EV Monitor project. For detailed feature documentation, see the `docs/` directory.

---

## 📋 Documentation Structure

**WICHTIG:** Wenn du ein Feature änderst, **MUSST** du die entsprechende Feature-Doc in `docs/features/` aktualisieren!

**Regel für Feature-Docs:**
- ✅ **Dokumentiere Features** - Was kann das Feature, wie funktioniert es, wie nutzt man es
- ❌ **KEINE Problem/Solution History** - Kein "Problem: X, Solution: Y, Bugfix: Z"
- ❌ **KEINE Debugging-Steps** - Keine detaillierten Error-Messages oder Trial-and-Error
- ❌ **KEINE Implementation Details** - Keine Code-Zeilen-Nummern, keine internen Algorithmen (außer wenn essentiell für Verständnis)
- 🎯 **Fokus:** Was der User wissen muss, nicht was während der Entwicklung passiert ist

**Ziel:** Feature-Docs sollen clean, fokussiert und wartbar bleiben - nicht zu Geschichtsbüchern werden!

---

## 🚨 Data Quality & Statistics Inclusion

**Flag-System:** `include_in_statistics` (Boolean)

**Regel:** Alle Statistiken, Public Aggregationen und WLTP-Vergleiche nutzen:
```sql
WHERE include_in_statistics = true
```

**Was wird excluded (`include_in_statistics = false`):**
- ❌ **Seed Data** - Test-Daten von `is_seed_data = true` Users
- ❌ **Tesla Imports** - Incomplete data (keine Kosten/Dauer)
- ❌ **Test Logs** - `data_source` beginnt mit `TEST_`

**Was wird included (`include_in_statistics = true`):**
- ✅ **USER_LOGGED** - Manuell eingetragene Logs (immer komplett)
- ✅ **SPRITMONITOR_IMPORT** - Vollständige Import-Daten
- ✅ **Future**: Tesla Logs wenn User manuell vervollständigt

**Implementation:**
- Flag wird bei Log-Creation automatisch gesetzt (Domain Logic in `EvLog.shouldIncludeInStatistics()`)
- Migration V14 setzt Flag für bestehende Daten
- Index für Performance: `idx_ev_log_include_in_statistics`

**Wo das Flag genutzt wird:**
- ✅ Public Model Stats (Community-Durchschnitte)
- ✅ User Statistics (eigene Durchschnitte)
- ✅ WLTP Consumption Calculations
- ❌ **NICHT** in: Dashboard Log-Liste, Heatmap (User sieht alle eigenen Logs)

**Demo-Mode für Seed Users:**
- 🎭 **Public APIs mit Optional JWT** - Seed Users sehen ALLE Test-Daten als Community-Daten
- **Wie es funktioniert:**
  - Public Model APIs (`/api/public/models`) akzeptieren optional JWT Token via `@AuthenticationPrincipal`
  - Wenn als Seed User eingeloggt: Query-Filter wird erweitert: `WHERE (include_in_statistics = true OR (:isSeedUser = true AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))`
  - **Security:** JWT wird validiert, ALLE Seed-User-Daten werden gezeigt (nicht nur eigene)
  - Frontend nutzt `apiClient` statt raw axios → JWT wird automatisch mitgeschickt
- **Scope:**
  - ✅ **Public Models Page** (`/modelle`) - Zeigt alle Modelle mit Seed-Daten
  - ✅ **Public Model Detail** (`/modelle/:brand/:model`) - Community-Stats inkl. aller Seed-Daten
  - ✅ **User Statistics** (`/dashboard/statistics`) - Eigene Logs inkl. eigener Seed-Daten
- **Use Case:** Seed Users können Platform mit vollem Datenumfang testen ohne echte Community-Daten zu benötigen
- **Implementation:** `PublicModelController`, `PublicModelService`, `EvLogService.getStatistics()`, `JpaEvLogRepository`

---

## 🎨 Design System

**Icons:** Heroicons Vue 24 Outline (`@heroicons/vue/24/outline`) - KEINE Emojis für UI-Elemente!

**Mobile-First:** Responsive Design mit Tailwind Breakpoints (sm:, md:, lg:)
- Mobile (<768px): Edge-to-Edge Layout, kein extra Padding/Container
- Desktop (≥768px): Max-Width Container, Shadow, Rounded Corners

---

## 🏠 Landing Page & Public Pages

**Route:** `/` - Public, SEO-optimiert mit Live Model Preview (Top 3-5 Models mit echten Community-Daten)

**Sections:** Hero ("WLTP vs. Realität") → Feature Cards (Tracking/Privacy/Community) → Live Models → Gamification Teaser → CTA → Footer

**Design:** Minimalistisch, viel Whitespace, Grün nur für CTAs

**SEO:** Live-Daten (Fresh Content), Internal Linking zu `/modelle/:brand/:model`, Long-Tail Keywords

**Legal Pages:** `/datenschutz` (DSGVO), `/impressum` (TMG §5), `/agb` (Nutzungsregeln)

**Routing:** `/` public, `/dashboard` auth required, eingeloggte User → `/dashboard` redirect

**Files:** `LandingPageView.vue`, `DatenschutzView.vue`, `ImpressumView.vue`, `AGBView.vue`

---

### Feature Documentation
- [Authentication & Authorization](docs/features/authentication.md) - JWT, Email-Verification, OAuth2
- [Charging Logs](docs/features/charging-logs.md) - EvLog CRUD, Geohashing, Location Search
- [Statistics & Heatmap](docs/features/statistics-heatmap.md) - Leaflet Maps, Charts, WLTP Delta
- [WLTP Crowdsourcing & Coins](docs/features/wltp-crowdsourcing.md) - Vehicle Specs, Coin System
- [Tesla Import](docs/features/tesla-import.md) - Tesla API Integration, Auto-Sync, Token Encryption

### Architecture Documentation
- [Database Schema](docs/architecture/database-schema.md) - Tables, Migrations, Constraints
- [Backend API](docs/architecture/backend-api.md) - (TODO) All Endpoints, DTOs, Error Codes
- [Frontend Routing](docs/architecture/frontend-routing.md) - (TODO) Vue Router, Guards

### Deployment Documentation
- [Local Development](docs/deployment/local-development.md) - dev.sh, Docker Compose, Troubleshooting
- [Production Setup](docs/deployment/production.md) - (TODO) Hetzner, nginx, SSL

### Planning Documentation
- [Road to Launch](docs/road-to-launch.md) - Beta Release Checklist, Timeline, Launch Strategy

---

## 👤 User Preferences & Session Settings

### Communication Style
- Sprich mit mir wie ein richtig guter Kumpel
- Nutze Nerd-Humor (Memes, Tech-Witze, Referenzen willkommen)
- **WICHTIG**: Sei witzig, ironisch und gerne sarkastisch!
- Locker und direkt, keine Corporate-Sprache
- "Du" statt "Sie" (bereits aktiv)
- Emojis sind erlaubt und erwünscht 🎉
- Bei doofen Bugs darf gelästert werden
- Wenn was smooth läuft, darf gefeiert werden

### Cost Management
- **WICHTIG**: Nach jeder abgeschlossenen Aufgabe die API-Kosten in Euro anzeigen
- Format: `**💰 Kosten für diese Aufgabe**: ~€X.XX`
- Am Ende: `**💰 Gesamt bisher**: ~€Y.YY`
- Arbeite so kosteneffizient wie möglich (bevorzuge leichtere Modelle wenn angemessen)
- Gib proaktiv Tipps, wie ich bei zukünftigen Tasks Kosten sparen kann

### Allowed Operations
- Alle `allow-edits` sind standardmäßig aktiviert
- Du darfst alle Dateien lesen, schreiben, editieren ohne explizite Nachfrage
- Bei Breaking Changes oder Daten-Löschungen trotzdem kurz Bescheid geben

### Security & Privacy First
**WICHTIG**: Bei jeder Code-Änderung OWASP Top 10 Vulnerabilities berücksichtigen!

**Immer prüfen:**
- ✅ SQL Injection - Prepared Statements / JPA
- ✅ XSS - Escaped User-Inputs im Frontend
- ✅ CSRF - CORS & Token-Validierung
- ✅ Authentication Bypass - JWT-Validierung in geschützten Endpoints
- ✅ Authorization - User darf nur EIGENE Daten sehen/ändern (userId-Checks!)
- ✅ Sensitive Data Exposure - Keine Plaintext-Passwörter, API-Keys als Env-Vars
- ✅ Command Injection - Keine unvalidierten Inputs in Shell-Commands

**DSGVO (Datenschutz):**
- Minimale Datenspeicherung (Privacy by Design)
- Geolocation: Nur Geohash speichern (5km Präzision), **NIEMALS** exakte GPS-Koordinaten
- User-Daten: Nur speichern was wirklich nötig ist
- Löschbarkeit: User muss Daten löschen können

**Bei neuen Features proaktiv prüfen:**
1. Welche Daten werden gespeichert? Sind sie wirklich nötig?
2. Wer darf diese Daten sehen? (Ownership-Checks!)
3. Können Inputs zu Injections führen? (Validation!)
4. Werden sensible Daten geloggt? (NIEMALS Passwörter/Tokens loggen!)

---

## 🚀 Projekt-Übersicht

**EV Monitor** – Elektroauto Ladetagebuch mit WLTP-Vergleich und Community-Features.

### Quick Facts
- 🏠 Landing Page (Public, SEO-optimiert, Live Model Preview)
- 📊 Charging Logs (kWh, Kosten, Standort, Dauer)
- 🚗 Vehicle Management (68 Marken, 100+ Modelle mit Battery Specs)
- ⚡ WLTP Crowdsourcing (Community-Verbrauchsdaten)
- 🔐 JWT Auth + Email-Verification (OAuth2 ready)
- 🗺️ Interactive Heatmap (Leaflet + Geohashing)
- 🪙 Gamification (Coin-System mit WLTP-Rewards)
- 📱 PWA-Ready
- 📄 Legal Pages (DSGVO-konforme Datenschutzerklärung, Impressum, AGB)

---

## 🛠️ Tech Stack

### Backend
- **Spring Boot**: 3.5.0
- **Java**: 21 (LTS)
- **Build**: Gradle Wrapper
- **DB**: PostgreSQL 15-alpine
- **Security**: Spring Security + JWT
- **Architecture**: Clean Architecture (Domain → Application → Infrastructure)

### Frontend
- **Vue.js**: 3.5.28 (Composition API)
- **Build**: Vite 7.3.1
- **TypeScript**: 5.9.3
- **CSS**: Tailwind 4.2.0
- **State**: Pinia 3.0.4
- **HTTP**: Axios (JWT Interceptor)

### Deployment
- **Docker Compose**: Multi-container
- **Nginx**: Reverse Proxy
- **Certbot**: Let's Encrypt SSL
- **Server**: Hetzner Cloud (ubuntu-4gb-nbg1-1, IP: 46.225.210.231)

---

## ⚡ Quick Start

```bash
./dev.sh
```

**Das startet:**
- ✅ PostgreSQL (Docker, Port 5432)
- ✅ Mailpit (Docker, Port 8025)
- ✅ Backend (nativ, Port 8080)
- ✅ Frontend (nativ, Port 5173)

**Zugriff:**
- 📱 Frontend: http://localhost:5173
- 🔧 Backend: http://localhost:8080
- 📧 Mailpit: http://localhost:8025
- 🗄️ DB: localhost:5432 (user: evmonitor, pass: evmonitor, db: ev_monitor)

**Test Users (DevDataSeeder):**
- max@ev-monitor.net / `123!"§`
- anna@ev-monitor.net / `123!"§`
- kurt@ev-monitor.net / `123!"§`

**Details:** Siehe [Local Development](docs/deployment/local-development.md)

---

## 📁 Projekt-Struktur

### Backend
```
backend/src/main/java/com/evmonitor/
├── domain/           # Entities (User, Car, EvLog, CoinLog, VehicleSpec)
├── application/      # Services, DTOs, Use Cases
└── infrastructure/   # Spring Config, Web, Persistence, Email
```

### Frontend
```
frontend/src/
├── views/           # Pages
│   ├── LandingPageView.vue          # Public Landing Page (/, SEO-optimiert)
│   ├── LoginView.vue                # Login
│   ├── RegisterView.vue             # Registrierung
│   ├── DashboardView.vue            # Dashboard (auth required)
│   ├── StatisticsView.vue           # Charts & Analytics (auth required)
│   ├── CarManagementView.vue        # Fahrzeuge verwalten (auth required)
│   ├── PublicModelsListView.vue     # /modelle - Alle Models (public, SEO)
│   ├── PublicModelView.vue          # /modelle/:brand/:model (public, SEO)
│   ├── DatenschutzView.vue          # DSGVO Datenschutzerklärung
│   ├── ImpressumView.vue            # Impressum (TMG §5)
│   └── AGBView.vue                  # Allgemeine Geschäftsbedingungen
├── components/      # Reusable (CarSelector, LocationSearch, ChargingHeatMap)
├── stores/          # Pinia (auth.ts)
├── router/          # Vue Router (Guards: requiresAuth, guestOnly)
└── api/             # Axios Services
```

### Database Migrations
```
backend/src/main/resources/db/migration/
├── V1__baseline.sql
├── V2__add_composite_indices.sql
├── ...
└── V11__create_tesla_connections.sql  (latest)
```

**Details:** Siehe [Database Schema](docs/architecture/database-schema.md)

---

## 🗂️ Wichtige Dateien

| File | Beschreibung |
|------|--------------|
| `dev.sh` | Start-Script für lokale Entwicklung |
| `stop-dev.sh` | Stop-Script (auto-generiert von dev.sh) |
| `.env.local` | Frontend Environment (VITE_API_BASE_URL) |
| `application.yml` | Backend Config (DB, JWT, Mail) |
| `application-dev.yml` | Dev-Profile Config (Actuator enabled) |
| `docker-compose.dev.yml` | Dev-Infrastruktur (DB + Mailpit) |
| `docker-compose.yml` | Production Setup |

---

## 🔑 Key Features Status

| Feature | Status | Doc Link |
|---------|--------|----------|
| JWT Authentication | ✅ Implementiert | [Authentication](docs/features/authentication.md) |
| Email Verification | ✅ Implementiert | [Authentication](docs/features/authentication.md) |
| OAuth2 SSO | 🟡 Infrastructure Ready | [Authentication](docs/features/authentication.md) |
| Charging Logs | ✅ Implementiert | [Charging Logs](docs/features/charging-logs.md) |
| Geohashing (Privacy) | ✅ Implementiert | [Charging Logs](docs/features/charging-logs.md) |
| Statistics & Charts | ✅ Implementiert | [Statistics & Heatmap](docs/features/statistics-heatmap.md) |
| Interactive Heatmap | ✅ Implementiert (Fixed 2026-03-01) | [Statistics & Heatmap](docs/features/statistics-heatmap.md) |
| WLTP Crowdsourcing | ✅ Implementiert | [WLTP Crowdsourcing](docs/features/wltp-crowdsourcing.md) |
| Coin System | 🟡 Teilweise (nur WLTP-Rewards) | [WLTP Crowdsourcing](docs/features/wltp-crowdsourcing.md) |
| Tesla API Integration | ✅ Implementiert (nur für Tesla-Besitzer sichtbar) | [Tesla Import](docs/features/tesla-import.md) |
| Sprit-Monitor Import | 🔴 TODO | - |

---

## 🐛 Bekannte Limitationen & TODOs

### High Priority
- [ ] **Mail-Config auf Prod-Server** (Mailgun/Brevo in `.env` eintragen)
- [ ] **Expired Tokens Cleanup** (Scheduled Job für email_verification_tokens)
- [ ] **Weitere Coin-Rewards** (EvLog creation, Streaks, Milestones)

### Medium Priority
- [ ] **API Pagination** (GET /api/logs gibt alle Logs zurück, >1000 könnte langsam werden)
- [ ] **WLTP Seed Data** (Populäre Modelle in V8 Migration)
- [ ] **WLTP Highway/City Support** (aktuell nur COMBINED)

### Low Priority
- [ ] **Location Search Caching** (Frontend)
- [ ] **Offline-Support** (PWA Service Worker)
- [ ] **GDPR User Deletion** (CASCADE Deletes + Data Export)

---

## 🚨 Common Issues & Fixes

### Heatmap zeigt keine Marker
**Fixed:** 2026-03-01 - mapContainer DIV wird jetzt immer gerendert (war conditional)

### Port 8080 already in use
```bash
lsof -ti:8080 | xargs kill -9
```

### Flyway Checksum Mismatch
```bash
cd backend && ./gradlew flywayRepair
# Oder: DB reset via dev.sh
```

### Frontend API Calls schlagen fehl
Prüfe `.env.local` und starte Frontend neu (Vite lädt .env nur beim Start!)

**Details:** Siehe [Local Development - Troubleshooting](docs/deployment/local-development.md#troubleshooting)

---

## 🌐 Production Deployment

**Server:** Hetzner Cloud ubuntu-4gb-nbg1-1

**Domain:** ev-monitor.net (+ www.ev-monitor.net)

**SSL:** Let's Encrypt (auto-renewal every 12h)

**User:** ihle (sudo, docker group)

**Details:** Siehe [Production Setup](docs/deployment/production.md) (TODO)

---

## 📚 Weitere Ressourcen

### Code Conventions
- **Backend:** Spring Boot Best Practices, Clean Architecture
- **Frontend:** Vue 3 Composition API, TypeScript Strict Mode
- **Commits:** Conventional Commits (feat:, fix:, chore:, docs:)

### Git Workflow
- **Main Branch:** `main` (protected)
- **Feature Branches:** `feature/{feature-name}`
- **Hotfixes:** `hotfix/{issue}`

### CI/CD
- **GitHub Actions:** (TODO) Auto-Deploy on push to main
- **Testing:** (TODO) Backend JUnit, Frontend Vitest

---

**Last Updated:** 2026-03-01
