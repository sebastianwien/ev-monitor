#!/bin/bash

# EV Monitor Deployment Script
# This script validates environment variables and deploys the application

set -e

echo "🚀 EV Monitor Deployment"
echo "========================"
echo ""

# Pull wallbox service repo if it exists alongside this repo
WALLBOX_DIR="$(cd "$(dirname "$0")/.." && pwd)/ev-monitor-wallbox"
if [ -d "$WALLBOX_DIR/.git" ]; then
  echo "🔄 Pulling Wallbox Service..."
  git -C "$WALLBOX_DIR" pull origin main
  echo ""
else
  echo "⚠️  Wallbox repo not found at $WALLBOX_DIR — skipping wallbox pull"
  echo "   Clone it: git clone git@github.com:YOUR_ORG/ev-monitor-wallbox.git $WALLBOX_DIR"
  echo ""
fi

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

if [ -z "$WALLBOX_DB_PASSWORD" ] || [ "$WALLBOX_DB_PASSWORD" == "CHANGE_ME_TO_STRONG_PASSWORD" ]; then
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

# Deploy
echo "🐳 Building and deploying containers..."
docker compose down
docker compose build --no-cache
docker compose up -d

echo ""
echo "⏳ Waiting for services to start..."
sleep 10

# Check if services are running
if docker compose ps | grep -q "Up"; then
  echo "✅ Services are running!"
  echo ""
  echo "🌍 Application deployed:"

  if [ "$DOMAIN" == "localhost" ]; then
    echo "   http://localhost"
  else
    echo "   http://$DOMAIN (redirects to HTTPS)"
    echo "   https://$DOMAIN"
  fi

  echo ""
  echo "📊 Check logs:"
  echo "   docker compose logs -f backend"
  echo "   docker compose logs -f nginx"
  echo ""
  echo "🔄 Restart services:"
  echo "   docker compose restart"
else
  echo "❌ ERROR: Services failed to start. Check logs:"
  echo "   docker compose logs"
  exit 1
fi
