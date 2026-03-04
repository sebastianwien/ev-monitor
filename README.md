# EV Monitor

**EV Monitor** is a full-stack web app for tracking Electric Vehicle charging sessions — logging kWh, costs, location, and duration, with vehicle management, WLTP data crowdsourcing, and a gamification coin system.

## 🤖 AI Assistant Context (Claude Code & Others)

This project is a multi-container application using Clean Architecture for the backend and a modern Vue+Vite setup for the frontend.

- **Backend**: Spring Boot 3.5 (Java 21, Gradle) → `./backend`
- **Frontend**: Vue 3, Vite, TypeScript, Tailwind CSS v4 → `./frontend`
- **Database**: PostgreSQL 15
- **Deployment**: Docker Compose + Nginx (reverse proxy) + Certbot (SSL)

When modifying this codebase, respect Clean Architecture boundaries in the backend (domain → application → infrastructure) and ensure dependency version compatibility (Java 21, Tailwind CSS v4 syntax).

## Features

- 📊 **Charging Logs** — kWh, costs in EUR, location (geohashed), duration, date
- 🚗 **Vehicle Management** — 68+ brands, 100+ models with battery capacity specs
- ⚡ **WLTP Crowdsourcing** — community-contributed range & consumption data
- 🔐 **Authentication** — JWT + email verification (24h token) + username
- 📧 **Email Verification** — token-based with resend + rate limiting
- 🪙 **Gamification** — earn coins for contributing WLTP data, feeding data, or bringing users to the platform (more rewards planned)
- 🌍 **Privacy-First Geohashing** — ~5km precision, no exact GPS coordinates stored
- 📈 **Statistics Dashboard** — charts & analytics (Chart.js)
- 📱 **PWA-Ready** — Progressive Web App

## Coming Soon: Wallbox Integration (Premium)

A dedicated **Wallbox Integration Service** is currently in development. It will automatically import charging sessions directly from your home wallbox — no manual logging required.

**Planned integrations:** go-e Charger, Easee, Wallbe, Heidelberg, KEBA, and more.

This will be available as a **premium add-on** and runs as a separate closed-source microservice alongside the open-source core. The EV Monitor core (this repository) remains free and open-source under AGPL-3.0.

## Project Structure

- [`/backend/README.md`](./backend/README.md) — Spring Boot REST API (Clean Architecture)
- [`/frontend/README.md`](./frontend/README.md) — Vue.js PWA
- `/nginx/` — Nginx config for reverse proxy + static file serving
- `docker-compose.yml` — Production orchestration (DB + Backend + Nginx + Certbot)
- `docker-compose.dev.yml` — Local dev infrastructure (DB + Mailpit only)
- `docker-compose.local.yml` — Full local stack (DB + Backend + Nginx + Mailpit)
- `deploy.sh` — Production deployment script (validates env, builds, restarts)
- `dev.sh` — Local dev startup script (starts DB, backend, frontend in one go)
- `stop-dev.sh` — Stops all local dev services
- `init-letsencrypt.sh` — First-time SSL certificate setup

## Getting Started

### Local Development (Recommended)

Use `dev.sh` to start the full stack with hot reload in one command:

```bash
./dev.sh
```

This starts:
1. PostgreSQL via Docker (`docker-compose.dev.yml`)
2. Spring Boot backend natively on `http://localhost:8080` (with `dev` profile)
3. Vite frontend dev server on `http://localhost:5173`

Logs stream to `./logs/backend.log` and `./logs/frontend.log`. Stop everything with:

```bash
./stop-dev.sh
```

**Test users** (created automatically by `DevDataSeeder` in dev profile):
| Email | Password |
|---|---|
| test1@ev-monitor.net | Test1234! |
| test2@ev-monitor.net | Test1234! |
| test3@ev-monitor.net | Test1234! |

Each user has 2 cars with ~70–80 charging logs over 1 year.

**Email testing** — Mailpit catches all outgoing emails locally:
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

## Sub-project Documentation

- [Backend README](./backend/README.md) — Architecture, running locally, building
- [Frontend README](./frontend/README.md) — Tech stack, dev server, building
