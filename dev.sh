#!/bin/bash

# EV Monitor - Local Development Startup Script
# This script starts the full development stack:
# - PostgreSQL (via Docker)
# - Backend (Spring Boot with dev profile + seed data)
# - Wallbox Service (Spring Boot)
# - Frontend (Vite dev server with hot reload)

set -e

echo "🚀 Starting EV Monitor Development Environment..."
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Resolve wallbox service directory relative to this script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WALLBOX_DIR="$SCRIPT_DIR/../ev-monitor-wallbox"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

# Check if wallbox service directory exists
if [ ! -d "$WALLBOX_DIR" ]; then
    echo -e "${YELLOW}⚠️  Wallbox service not found at $WALLBOX_DIR — skipping wallbox${NC}"
    SKIP_WALLBOX=true
else
    SKIP_WALLBOX=false
fi

# Step 1: Start PostgreSQL
echo -e "${BLUE}📦 Step 1/5: Starting PostgreSQL...${NC}"
docker compose -f docker-compose.dev.yml up -d

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}⏳ Waiting for PostgreSQL to be ready...${NC}"
max_attempts=30
attempt=0
until docker compose -f docker-compose.dev.yml exec -T db pg_isready -U evmonitor -d ev_monitor > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo -e "${RED}❌ PostgreSQL failed to start after ${max_attempts} seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}✅ PostgreSQL is ready${NC}"

# Ensure wallbox database exists (idempotent — safe to run every time)
if [ "$SKIP_WALLBOX" = false ]; then
    docker compose -f docker-compose.dev.yml exec -T db \
        psql -U evmonitor -d postgres -c "CREATE DATABASE ev_monitor_wallbox;" 2>/dev/null || true
fi
echo ""

# Step 2: Check if tables need to be dropped (optional)
echo -e "${BLUE}🗄️  Step 2/5: Database Setup${NC}"
read -p "Do you want to drop all tables for a fresh start? (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🗑️  Dropping all tables...${NC}"
    docker compose -f docker-compose.dev.yml exec -T db psql -U evmonitor -d ev_monitor <<EOF
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO evmonitor;
GRANT ALL ON SCHEMA public TO public;
EOF
    echo -e "${GREEN}✅ Database reset (all tables dropped)${NC}"
else
    echo -e "${YELLOW}⏭️  Skipping table drop${NC}"
fi
echo ""

# Create logs directory if not exists
mkdir -p logs

# Step 3: Start Backend
echo -e "${BLUE}⚙️  Step 3/5: Starting Backend (Spring Boot)...${NC}"
echo -e "${YELLOW}📝 Backend will start on http://localhost:8080${NC}"
echo -e "${YELLOW}📝 DevDataSeeder will create 3 test users + 6 cars + ~370 charging logs${NC}"
echo ""

cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
cd ..

# Save PID for cleanup
echo $BACKEND_PID > .backend.pid

# Wait for Backend to be ready
echo -e "${YELLOW}⏳ Waiting for Backend to start...${NC}"
max_attempts=60
attempt=0
until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8080 > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo -e "${RED}❌ Backend failed to start after ${max_attempts} seconds${NC}"
        echo -e "${YELLOW}Check logs/backend.log for details${NC}"
        kill $BACKEND_PID 2>/dev/null || true
        exit 1
    fi
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo -e "${RED}❌ Backend process died${NC}"
        echo -e "${YELLOW}Check logs/backend.log for details${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}✅ Backend is ready${NC}"
echo ""

# Step 4: Start Wallbox Service
echo -e "${BLUE}🔌 Step 4/5: Starting Wallbox Service...${NC}"
if [ "$SKIP_WALLBOX" = true ]; then
    echo -e "${YELLOW}⏭️  Wallbox service directory not found — skipping${NC}"
    WALLBOX_PID=""
else
    echo -e "${YELLOW}📝 Wallbox Service will start on http://localhost:8090${NC}"
    echo ""

    cd "$WALLBOX_DIR"
    DB_USER=evmonitor \
    DB_PASSWORD=evmonitor \
    EVMONITOR_INTERNAL_TOKEN=dev-internal-token-change-in-prod \
    JWT_SECRET=dev-secret-key-CHANGE-IN-PRODUCTION-THIS-MUST-BE-AT-LEAST-64-CHARS-LONG-FOR-HS512-ALGORITHM-12345678901234567890 \
    ./gradlew bootRun > "$SCRIPT_DIR/logs/wallbox.log" 2>&1 &
    WALLBOX_PID=$!
    cd "$SCRIPT_DIR"

    echo $WALLBOX_PID > .wallbox.pid

    echo -e "${YELLOW}⏳ Waiting for Wallbox Service to start...${NC}"
    max_attempts=60
    attempt=0
    until curl -s http://localhost:8090/api/wallbox/connections > /dev/null 2>&1 || nc -z localhost 8090 2>/dev/null; do
        attempt=$((attempt + 1))
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${YELLOW}⚠️  Wallbox Service did not respond after ${max_attempts}s — continuing anyway${NC}"
            echo -e "${YELLOW}Check logs/wallbox.log for details${NC}"
            break
        fi
        if ! kill -0 $WALLBOX_PID 2>/dev/null; then
            echo -e "${YELLOW}⚠️  Wallbox Service process died — continuing without it${NC}"
            echo -e "${YELLOW}Check logs/wallbox.log for details${NC}"
            WALLBOX_PID=""
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""
    if [ -n "$WALLBOX_PID" ] && kill -0 $WALLBOX_PID 2>/dev/null; then
        echo -e "${GREEN}✅ Wallbox Service is ready${NC}"
    fi
    echo ""
fi

# Step 5: Start Frontend
echo -e "${BLUE}🎨 Step 5/5: Starting Frontend (Vite Dev Server)...${NC}"
echo -e "${YELLOW}📝 Frontend will start on http://localhost:5173${NC}"
echo ""

cd frontend
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

# Save PID for cleanup
echo $FRONTEND_PID > .frontend.pid

# Wait a bit for frontend to start
sleep 3

echo ""
echo -e "${GREEN}✅ Development environment is ready!${NC}"
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🎉 EV Monitor is running!${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}📱 Frontend:${NC}        http://localhost:5173"
echo -e "${BLUE}🔧 Backend:${NC}         http://localhost:8080"
if [ "$SKIP_WALLBOX" = false ]; then
echo -e "${BLUE}🔌 Wallbox Service:${NC} http://localhost:8090"
fi
echo -e "${BLUE}🗄️  Database:${NC}       localhost:5432 (user: evmonitor, pass: evmonitor)"
echo -e "${BLUE}📧 Mailpit:${NC}         http://localhost:8025"
echo ""
echo -e "${YELLOW}👤 Test Users (created by DevDataSeeder):${NC}"
echo "   - max_e_driver (max@ev-monitor.net) / 123!\"§"
echo "   - anna_ampere (anna@ev-monitor.net) / 123!\"§"
echo "   - kurt_kilowatt (kurt@ev-monitor.net) / 123!\"§"
echo ""
echo -e "${YELLOW}📊 Each user has 2 cars with ~70-80 charging logs over 1 year${NC}"
echo ""
echo -e "${RED}🛑 To stop: Run ./stop-dev.sh or press Ctrl+C${NC}"
echo ""

# Create stop script
cat > stop-dev.sh <<'STOP_SCRIPT'
#!/bin/bash
echo "🛑 Stopping EV Monitor Development Environment..."

# Kill backend
if [ -f .backend.pid ]; then
    BACKEND_PID=$(cat .backend.pid)
    if kill -0 $BACKEND_PID 2>/dev/null; then
        echo "⏹️  Stopping Backend (PID: $BACKEND_PID)..."
        kill $BACKEND_PID
    fi
    rm .backend.pid
fi

# Kill wallbox service
if [ -f .wallbox.pid ]; then
    WALLBOX_PID=$(cat .wallbox.pid)
    if kill -0 $WALLBOX_PID 2>/dev/null; then
        echo "⏹️  Stopping Wallbox Service (PID: $WALLBOX_PID)..."
        kill $WALLBOX_PID
    fi
    rm .wallbox.pid
fi

# Kill frontend
if [ -f .frontend.pid ]; then
    FRONTEND_PID=$(cat .frontend.pid)
    if kill -0 $FRONTEND_PID 2>/dev/null; then
        echo "⏹️  Stopping Frontend (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID
    fi
    rm .frontend.pid
fi

# Stop PostgreSQL
echo "⏹️  Stopping PostgreSQL..."
docker compose -f docker-compose.dev.yml down

echo "✅ All services stopped"
STOP_SCRIPT

chmod +x stop-dev.sh

# Create logs directory if not exists
mkdir -p logs

# Trap Ctrl+C and cleanup
trap './stop-dev.sh; exit' INT TERM

# Keep script running and tail logs
echo -e "${BLUE}📋 Tailing logs (Ctrl+C to stop all services)...${NC}"
echo ""

LOG_FILES="logs/backend.log logs/frontend.log"
[ -f logs/wallbox.log ] && LOG_FILES="$LOG_FILES logs/wallbox.log"
tail -f $LOG_FILES
