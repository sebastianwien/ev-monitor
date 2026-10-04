#!/bin/bash

# EV Monitor Deployment Script
# This script validates environment variables and deploys the application

set -e

echo "🚀 EV Monitor Deployment"
echo "========================"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
  echo "❌ ERROR: .env file missing!"
  echo ""
  echo "Please create .env file:"
  echo "  cp .env.example .env"
  echo "  nano .env  # Fill in your secrets"
  echo ""
  exit 1
fi

# Source .env file
set -a
source .env
set +a

# Validate critical environment variables
ERRORS=0

echo "🔍 Validating environment variables..."

if [ -z "$JWT_SECRET" ]; then
  echo "❌ JWT_SECRET is missing or empty!"
  ERRORS=$((ERRORS + 1))
elif [ "$JWT_SECRET" == "CHANGE_ME_TO_RANDOM_64_CHAR_STRING_USE_OPENSSL_RAND" ]; then
  echo "❌ JWT_SECRET is not configured (still default value)!"
  echo "   Generate one: openssl rand -base64 64"
  ERRORS=$((ERRORS + 1))
fi

if [ -z "$POSTGRES_PASSWORD" ]; then
  echo "❌ POSTGRES_PASSWORD is missing or empty!"
  ERRORS=$((ERRORS + 1))
elif [ "$POSTGRES_PASSWORD" == "CHANGE_ME_TO_STRONG_PASSWORD_MIN_32_CHARS" ]; then
  echo "❌ POSTGRES_PASSWORD is not configured (still default value)!"
  echo "   Generate one: openssl rand -base64 32"
  ERRORS=$((ERRORS + 1))
fi

if [ -z "$DOMAIN" ]; then
  echo "❌ DOMAIN is missing or empty!"
  ERRORS=$((ERRORS + 1))
fi

if [ -z "$ALLOWED_ORIGINS" ]; then
  echo "❌ ALLOWED_ORIGINS is missing or empty!"
  ERRORS=$((ERRORS + 1))
fi

if [ -f docker-compose.full.yml ] && { [ -z "$WALLBOX_DB_PASSWORD" ] || [ "$WALLBOX_DB_PASSWORD" == "CHANGE_ME_TO_STRONG_PASSWORD" ]; }; then
  echo "❌ WALLBOX_DB_PASSWORD is missing or not configured!"
  ERRORS=$((ERRORS + 1))
fi

if [ -z "$INTERNAL_SERVICE_TOKEN" ] || [ "$INTERNAL_SERVICE_TOKEN" == "CHANGE_ME_TO_RANDOM_SECRET" ]; then
  echo "❌ INTERNAL_SERVICE_TOKEN is missing or not configured!"
  echo "   Generate one: openssl rand -base64 32"
  ERRORS=$((ERRORS + 1))
fi

if [ $ERRORS -gt 0 ]; then
  echo ""
  echo "❌ $ERRORS validation error(s) found. Please fix .env and try again."
  exit 1
fi

echo "✅ All required environment variables are set"
echo ""

# Check if running first time (no SSL cert yet)
if [ ! -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ] && [ "$DOMAIN" != "localhost" ]; then
  echo "⚠️  WARNING: No SSL certificate found for $DOMAIN"
  echo ""
  echo "After deployment completes, run:"
  echo "  ./init-letsencrypt.sh"
  echo ""
fi

# Use full compose file if available (includes private microservices),
# otherwise fall back to community docker-compose.yml
if [ -f docker-compose.full.yml ]; then
  COMPOSE_FILE="-f docker-compose.full.yml"
else
  COMPOSE_FILE=""
fi

# Deploy
# Note: only build and recreate services owned by this repo.
# connectors-service and wallbox-service have their own deploy pipelines.
echo "🐳 Building backend + nginx..."
docker compose $COMPOSE_FILE build backend nginx

echo ""
echo "🔄 Ensuring db + certbot are running..."
docker compose $COMPOSE_FILE up -d db certbot

echo ""
echo "🔄 Switching to new backend + nginx containers..."
# --wait blockiert, bis backend healthy ist (Healthcheck in docker-compose.full.yml),
# und schlägt fehl, wenn es das innerhalb des Timeouts nicht wird.
if ! docker compose $COMPOSE_FILE up -d --no-deps --force-recreate --wait --wait-timeout 180 backend nginx; then
  echo "❌ ERROR: backend/nginx not healthy. Check logs:"
  echo "   docker compose $COMPOSE_FILE logs backend --tail=100"
  exit 1
fi

# Reload nginx to pick up new container IPs after restart
echo "🔄 Reloading nginx configuration..."
docker compose $COMPOSE_FILE exec -T nginx nginx -s reload || echo "⚠️  nginx reload failed"

echo "✅ Services are running!"
echo ""
echo "🌍 Application deployed: https://$DOMAIN"
# Cron (docker-cleanup) wird einmalig per scripts/server-setup.sh eingerichtet, nicht bei jedem Deploy.
