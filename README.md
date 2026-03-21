# EV Monitor

**EV Monitor** is a full-stack web app for tracking Electric Vehicle charging sessions. Log kWh, costs, location, and duration — and compare your real-world consumption against WLTP ratings using community-contributed data.

Live at **[ev-monitor.net](https://ev-monitor.net)**

---

## Features

### Core
- **Charging Logs** — kWh, costs in EUR, location (geohashed for privacy), duration, date
- **Vehicle Management** — 68+ brands, 100+ models with battery capacity specs
- **Statistics Dashboard** — charts, seasonal breakdowns, WLTP delta analysis (Chart.js)
- **Interactive Heatmap** — visualize charging locations on a map (Leaflet, ~5km precision)
- **WLTP Crowdsourcing** — community-contributed real-world range & consumption data per model
- **Gamification** — earn coins for logging data, adding vehicle specs, and referring users
- **Leaderboard** — community ranking with optional visibility toggle
- **Privacy-First Geohashing** — no exact GPS coordinates ever stored

### Authentication & Accounts
- **JWT Authentication** — token-based, 7-day sessions
- **Email Verification** — 24h token with resend + rate limiting
- **Password Reset** — email-based two-step flow
- **OAuth2** — infrastructure ready (Google/GitHub)
- **User Settings** — profile, notification preferences, leaderboard visibility

### Import & Integrations
- **Tesla Import** — automatic session sync via Tesla Fleet API (with encrypted token storage)
- **Spritmonitor Import** — one-click import of existing Spritmonitor history
- **Manual Import** — CSV/JSON import with session grouping logic
- **Public Upload API** — REST API with API keys for wallboxes, scripts, home automation

### Public Pages (SEO-optimized)
- **Model Overview** (`/modelle`) — community averages per EV model
- **Brand Pages** (`/modelle/:brand`) — all models per brand
- **Model Detail Pages** (`/modelle/:brand/:model`) — WLTP vs. real consumption, seasonal data
- **Model Comparison** — side-by-side comparison of multiple models

### Premium (Beta)
- **Wallbox Integration** — automatic import from go-e Charger and more (runs as separate closed-source microservice)
- **Stripe Subscription** — infrastructure in place, currently in beta (`PREMIUM_ENABLED=false`)

---

## Tech Stack

### Backend
| | |
|---|---|
| Framework | Spring Boot 3.5.0 |
| Language | Java 21 |
| Build | Gradle (Wrapper) |
| Database | PostgreSQL 15 |
| Security | Spring Security + JWT |
| Architecture | Clean Architecture (domain → application → infrastructure) |

### Frontend
| | |
|---|---|
| Framework | Vue 3.5.28 (Composition API) |
| Build | Vite 7.3.1 |
| Language | TypeScript 5.9.3 |
| CSS | Tailwind CSS 4.2.0 |
| State | Pinia 3.0.4 |
| HTTP | Axios (JWT Interceptor) |
| Charts | Chart.js 4.5.1 + vue-chartjs |
| Maps | Leaflet 1.9.4 + leaflet.heat + markercluster |
| Icons | Heroicons Vue 2.2.0 |

### Infrastructure
- **Docker Compose** — multi-container setup
- **Nginx** — reverse proxy + static file serving
- **Certbot** — Let's Encrypt SSL (auto-renewal)
- **Hosting** — Hetzner Cloud

---

## Project Structure

```
/
├── backend/              # Spring Boot REST API
├── frontend/             # Vue.js PWA
├── nginx/                # Nginx config (reverse proxy)
├── docker-compose.yml         # Production orchestration
├── docker-compose.dev.yml     # Local dev: DB + Mailpit only
├── docker-compose.local.yml   # Full local stack with Nginx
├── dev.sh                # One-command local dev startup
├── stop-dev.sh           # Stops all local dev processes
├── deploy.sh             # Production deployment script
└── init-letsencrypt.sh   # First-time SSL certificate setup
```

### Backend Structure
```
backend/src/main/java/com/evmonitor/
├── domain/           # Entities: User, Car, EvLog, CoinLog, VehicleSpec
├── application/      # Services, DTOs, Use Cases
└── infrastructure/   # Spring config, Web, Persistence, Email, Security
```

### Frontend Views
```
frontend/src/views/
├── LandingPageView.vue          # / — Public landing page (SEO)
├── PublicModelsListView.vue     # /modelle — All models (SEO)
├── PublicBrandView.vue          # /modelle/:brand (SEO)
├── PublicModelView.vue          # /modelle/:brand/:model (SEO)
├── PublicModelsCompareView.vue  # Model comparison
├── LoginView.vue
├── RegisterView.vue
├── ForgotPasswordView.vue
├── ResetPasswordView.vue
├── VerifyEmailView.vue
├── OAuth2RedirectHandler.vue
├── DashboardView.vue            # Main dashboard (auth required)
├── LogFormView.vue              # Add/edit charging log
├── StatisticsView.vue           # Charts & analytics
├── CarManagementView.vue        # Vehicle management
├── ImportsView.vue              # All imports: Tesla, Spritmonitor, CSV, go-e
├── WallboxSetupView.vue         # Wallbox connection management
├── LeaderboardView.vue          # Community leaderboard
├── CoinHistoryView.vue          # Coin transaction history
├── SettingsView.vue             # Account settings
├── UpgradeView.vue              # Premium subscription
├── UpgradeSuccessView.vue
├── UpgradeCancelView.vue
├── DatenschutzView.vue          # DSGVO privacy policy
├── ImpressumView.vue            # Legal notice (TMG §5)
├── AGBView.vue                  # Terms of service
└── NotFoundView.vue             # 404
```

---

## Getting Started

### Local Development (Recommended)

```bash
./dev.sh
```

Starts the full stack with hot reload:
1. PostgreSQL via Docker (`docker-compose.dev.yml`)
2. Mailpit (email testing) via Docker
3. Spring Boot backend natively on `http://localhost:8080` (dev profile)
4. Vite frontend dev server on `http://localhost:5173`

Logs stream to `./logs/backend.log` and `./logs/frontend.log`. Stop everything with:

```bash
./stop-dev.sh
```

**Test users** (seeded automatically by `DevDataSeeder` in dev profile):

| Email | Password | Username |
|---|---|---|
| test1@ev-monitor.net | `123!"§` | max_e_driver |
| test2@ev-monitor.net | `123!"§` | anna_ampere |
| test3@ev-monitor.net | `123!"§` | kurt_kilowatt |

Each user has 2 cars with ~70-80 charging logs spread over 1 year.

**Email testing** — Mailpit catches all outgoing emails:
- Web UI: `http://localhost:8025`
- SMTP: `localhost:1025`

### Manual Local Dev

```bash
# 1. Start DB + Mailpit
docker compose -f docker-compose.dev.yml up -d

# 2. Start Backend (dev profile)
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# 3. Start Frontend (hot reload)
cd frontend && npm run dev
```

### Full Local Stack (Production-Like)

```bash
docker compose -f docker-compose.local.yml up --build
```

Available at `http://localhost`.

---

## Database

PostgreSQL with Flyway migrations. Current schema: **V47** (`V47__remove_spritmonitor_session_groups.sql`).

Migrations live in `backend/src/main/resources/db/migration/`.

---

## Deployment

### Prerequisites

- Docker + Docker Compose on the target server
- A `.env` file (copy from `.env.example`):

```bash
cp .env.example .env
```

Required environment variables:

```bash
DOMAIN=your-domain.com
POSTGRES_USER=evmonitor
POSTGRES_PASSWORD=<strong-password>
POSTGRES_DB=ev_monitor
JWT_SECRET=<64-char-random-string>          # openssl rand -base64 64
JWT_EXPIRATION_MS=604800000
ALLOWED_ORIGINS=https://your-domain.com
SPRING_PROFILES_ACTIVE=prod

# Mail (Mailgun/Brevo recommended)
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=<username>
MAIL_PASSWORD=<password>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
APP_MAIL_FROM=noreply@your-domain.com
APP_BASE_URL=https://your-domain.com
```

### Deploy

```bash
./deploy.sh
```

The script validates all required env vars, builds multi-stage Docker images, and starts the stack in detached mode.

### Useful Commands

```bash
# Logs
docker compose logs -f backend
docker compose logs -f nginx

# Restart a service
docker compose restart backend

# Manual SSL renewal (auto-renews every 12h via certbot container)
docker compose run --rm --entrypoint certbot certbot renew
```

---

## Sub-project Documentation

- [Backend README](./backend/README.md) — Architecture, running locally, building
- [Frontend README](./frontend/README.md) — Tech stack, dev server, building
- [Feature Docs](./docs/) — Detailed documentation per feature

---

## License

The EV Monitor core is open-source under **AGPL-3.0**. The Wallbox Integration microservice is a separate, closed-source component.
