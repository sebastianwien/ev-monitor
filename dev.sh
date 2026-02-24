#!/bin/bash

# EV Monitor - Local Development Startup Script
# This script starts the full development stack:
# - PostgreSQL (via Docker)
# - Backend (Spring Boot with dev profile + seed data)
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

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

# Step 1: Start PostgreSQL
echo -e "${BLUE}📦 Step 1/4: Starting PostgreSQL...${NC}"
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
echo ""

# Step 2: Check if tables need to be dropped (optional)
echo -e "${BLUE}🗄️  Step 2/4: Database Setup${NC}"
read -p "Do you want to drop all tables for a fresh start? (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🗑️  Dropping all tables...${NC}"
    docker compose -f docker-compose.dev.yml exec -T db psql -U evmonitor -d ev_monitor <<EOF
DROP TABLE IF EXISTS ev_log CASCADE;
DROP TABLE IF EXISTS car CASCADE;
DROP TABLE IF EXISTS coin_log CASCADE;
DROP TABLE IF EXISTS vehicle_specification CASCADE;
DROP TABLE IF EXISTS users CASCADE;
EOF
    echo -e "${GREEN}✅ Tables dropped${NC}"
else
    echo -e "${YELLOW}⏭️  Skipping table drop${NC}"
fi
echo ""

# Step 3: Start Backend
echo -e "${BLUE}⚙️  Step 3/4: Starting Backend (Spring Boot)...${NC}"
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
    # Check if backend process is still running
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

# Step 4: Start Frontend
echo -e "${BLUE}🎨 Step 4/4: Starting Frontend (Vite Dev Server)...${NC}"
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
echo -e "${BLUE}📱 Frontend:${NC}  http://localhost:5173"
echo -e "${BLUE}🔧 Backend:${NC}   http://localhost:8080"
echo -e "${BLUE}🗄️  Database:${NC} localhost:5432 (user: evmonitor, pass: evmonitor, db: ev_monitor)"
echo ""
echo -e "${YELLOW}👤 Test Users (created by DevDataSeeder):${NC}"
echo "   - test1@ev-monitor.net / Test1234!"
echo "   - test2@ev-monitor.net / Test1234!"
echo "   - test3@ev-monitor.net / Test1234!"
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
tail -f logs/backend.log logs/frontend.log
