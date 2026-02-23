# EV Monitor

Welcome to the **EV Monitor** project repository. This is a modern Progressive Web App (PWA) designed for logging and tracking Electric Vehicle (EV) metrics, such as distance driven, energy consumption, outside temperature, and driving style.

## 🤖 AI Assistant Context (Claude Code & Others)
This project is structured as a multi-container application utilizing a strictly separated Clean Architecture for the backend and a modern Vue+Vite setup for the frontend. 

- **Backend**: Spring Boot 3.5 (Java 21, Gradle) -> Location: `./backend`
- **Frontend**: Vue 3, Vite, TypeScript, Tailwind CSS v4 (PWA) -> Location: `./frontend`
- **Database**: PostgreSQL 15
- **Deployment**: Docker Compose alongside Nginx (as a reverse proxy) and Certbot.

When working on this codebase, please respect the architectural boundaries (especially in the Java backend) and ensure that updates to dependencies account for the specific versions (e.g., Java 21 compatibility, Tailwind CSS v4 syntax).

## Project Structure

- [`/backend/README.md`](./backend/README.md): Contains the Spring Boot application source code, adhering to Clean Architecture principles.
- [`/frontend/README.md`](./frontend/README.md): Contains the Vue.js Progressive Web Application.
- `/nginx/`: Contains the Nginx configuration template and Dockerfile for serving the frontend and reverse-proxying API requests to the backend.
- `docker-compose.yml`: Defines the local and production orchestration of the services (App, DB, Nginx, Certbot).
- `deploy.sh`: Automated deployment script for pulling changes, building, and restarting the Docker composition.

## Getting Started

### Local Development (via Docker)

The easiest way to run the entire stack locally is via Docker Compose:

```bash
# Start the database, backend, and Nginx wrapper
docker compose up --build
```
The application will be available at `http://localhost`. API calls are routed entirely via Nginx to the Spring Boot backend (`http://localhost/api/...`), completely eliminating CORS issues.

### Sub-projects Development
If you need to run the services individually for faster hot-reloading development loops, please refer to the specific component README files:
- [Backend Instructions](./backend/README.md)
- [Frontend Instructions](./frontend/README.md)

## Deployment Instructions

To deploy to a production Ubuntu environment (e.g., Hetzner):
1. Ensure Docker and Docker Compose are installed on the host.
2. Clone this repository.
3. Configure your required environment variables. Create a `.env` file containing:
   - `DOMAIN`
   - `POSTGRES_USER`
   - `POSTGRES_PASSWORD`
   - `POSTGRES_DB`
4. Execute `./deploy.sh` to build the isolated Docker multistage images and spin up the stack safely in detached mode. If this is the execution for the first time, you may need to initialize dummy certificates or run an initial certbot command to satisfy Nginx's strictly enforced SSL requirements on port 443.
