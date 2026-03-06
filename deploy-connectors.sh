#!/bin/bash

# EV Monitor — Deploy only the connectors service
# Called by the ev-monitor-connectors GitHub Actions workflow.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONNECTORS_DIR="$SCRIPT_DIR/../ev-monitor-connectors"

# Pull latest connectors code
if [ -d "$CONNECTORS_DIR/.git" ]; then
    echo "🔄 Pulling ev-monitor-connectors..."
    git -C "$CONNECTORS_DIR" pull origin main
    echo ""
else
    echo "❌ ev-monitor-connectors not found at $CONNECTORS_DIR"
    echo "   Clone it: git clone git@github.com:YOUR_ORG/ev-monitor-connectors.git $CONNECTORS_DIR"
    exit 1
fi

# Check .env
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    echo "❌ .env file missing in $SCRIPT_DIR"
    exit 1
fi
set -a; source "$SCRIPT_DIR/.env"; set +a

# Validate connectors-specific env vars
ERRORS=0
[ -z "$POSTGRES_PASSWORD" ] && echo "❌ POSTGRES_PASSWORD missing" && ERRORS=$((ERRORS+1))
[ -z "$INTERNAL_SERVICE_TOKEN" ] && echo "❌ INTERNAL_SERVICE_TOKEN missing" && ERRORS=$((ERRORS+1))
[ -z "$JWT_SECRET" ] && echo "❌ JWT_SECRET missing" && ERRORS=$((ERRORS+1))
if [ $ERRORS -gt 0 ]; then echo "❌ $ERRORS error(s). Aborting."; exit 1; fi

echo "🐳 Rebuilding and restarting connectors-service + nginx..."
cd "$SCRIPT_DIR"
docker compose build --no-cache connectors-service nginx
docker compose up -d connectors-service nginx

echo ""
echo "⏳ Waiting for connectors-service to start..."
sleep 10

if docker compose ps connectors-service | grep -q "Up"; then
    echo "✅ connectors-service is running"
else
    echo "❌ connectors-service failed to start"
    docker compose logs --tail=50 connectors-service
    exit 1
fi
