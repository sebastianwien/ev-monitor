# Stage 1: Build Frontend
FROM node:22-alpine AS build-stage
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# Set API base URL to use Nginx proxy
ENV VITE_API_BASE_URL=/api
# Set Reddit Pixel ID (passed as build arg from .env)
ARG VITE_REDDIT_PIXEL_ID
ENV VITE_REDDIT_PIXEL_ID=${VITE_REDDIT_PIXEL_ID}
# Google OAuth2 (set to true when GOOGLE_CLIENT_ID/SECRET are configured)
ARG VITE_GOOGLE_OAUTH_ENABLED=false
ENV VITE_GOOGLE_OAUTH_ENABLED=${VITE_GOOGLE_OAUTH_ENABLED}
RUN npm run build

# Stage 2: nginx mit Konfiguration, ohne Frontend
FROM nginx:1.31.6-alpine AS runtime
# Copy basic nginx config
COPY nginx/nginx.conf /etc/nginx/nginx.conf
# Copy site configuration template
COPY nginx/conf.d/app.conf.template /etc/nginx/templates/default.conf.template
# Backend-Upstream (Prod: per Mount überschrieben, siehe Datei)
COPY nginx/state /etc/nginx/state
# Copy .well-known files (Tesla Fleet API public key etc.)
COPY nginx/well-known /usr/share/nginx/well-known

# Prod (CI): nur Konfiguration, das Frontend kommt als eigenes Image (Target frontend)
# und liegt auf dem Server unter /opt/ev-monitor/frontend, gemountet nach /srv/frontend.
# Image-Tag = Git-Tree-Hash von nginx/, nginx wird nur bei Config-Änderung neu erzeugt.
# Aufruf: docker build --target prebuilt
FROM runtime AS prebuilt

# Prod (CI): das im Testlauf gebaute frontend/dist als reiner Dateiträger. Der Deploy
# kopiert es per docker create/cp nach /opt/ev-monitor/frontend/releases/<sha>.
# Die VITE_*-Variablen setzt der CI-Build (siehe deploy.yml).
FROM scratch AS frontend
COPY frontend/dist /dist

# Stage 3 (Default): baut das Frontend selbst, für lokalen Bau und Community-Setup
FROM runtime
COPY --from=build-stage /app/dist /srv/frontend/current
