#!/bin/bash
set -e

echo "Starting Deployment for ev-monitor..."

# Load environment variables if present
if [ -f .env ]; then
  set -a
  source .env
  set +a
fi

# Set defaults if env not provided
export DOMAIN=${DOMAIN:-yourdomain.com}
export POSTGRES_USER=${POSTGRES_USER:-evmonitor}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-SuperSecretPassword123}
export POSTGRES_DB=${POSTGRES_DB:-ev_monitor}

echo "Updating codebase..."
git pull origin main || echo "Git pull skipped or failed, proceeding with local files."

echo "Building and starting containers in detached mode..."
docker compose up -d --build

echo "Deployment finished. Application is starting up."
echo "Ensure your DNS points to this server IP."
echo "If this is your first run with SSL, you might need an init-letsencrypt bootstrap script to generate the initial certificates before Nginx can fully start."
