#!/bin/bash

# EV Monitor - Local Development Startup Script
# Starts the core development stack:
# - PostgreSQL + Mailpit (via Docker)
# - Backend (Spring Boot, dev profile)
# - Frontend (Vite dev server, hot reload)

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Starting EV Monitor Development Environment...${NC}"
echo ""

if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

# Step 1: Start PostgreSQL + Mailpit
echo -e "${BLUE}Step 1/2: Starting PostgreSQL + Mailpit...${NC}"
docker compose -f docker-compose.dev.yml up -d

echo -e "${YELLOW}Waiting for PostgreSQL...${NC}"
max_attempts=30
attempt=0
until docker compose -f docker-compose.dev.yml exec -T db pg_isready -U evmonitor -d ev_monitor > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo -e "${RED}PostgreSQL failed to start after ${max_attempts}s${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}PostgreSQL ready${NC}"
echo ""

mkdir -p logs

# Step 2: Start Backend
echo -e "${BLUE}Step 2/2: Starting Backend...${NC}"
echo -e "${YELLOW}Backend -> http://localhost:8080${NC}"
echo ""

cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo $BACKEND_PID > .backend.pid

echo -e "${YELLOW}Waiting for Backend...${NC}"
max_attempts=60
attempt=0
until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8080 > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo -e "${RED}Backend failed to start after ${max_attempts}s${NC}"
        echo -e "${YELLOW}Check logs/backend.log for details${NC}"
        kill $BACKEND_PID 2>/dev/null || true
        exit 1
    fi
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo -e "${RED}Backend process died${NC}"
        echo -e "${YELLOW}Check logs/backend.log for details${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}Backend ready${NC}"
echo ""

# Step 3: Start Frontend
echo -e "${BLUE}Starting Frontend...${NC}"
echo -e "${YELLOW}Frontend -> http://localhost:5173${NC}"
echo ""

cd frontend
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo $FRONTEND_PID > .frontend.pid

sleep 3

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}EV Monitor is running!${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}Frontend:${NC}   http://localhost:5173"
echo -e "${BLUE}Backend:${NC}    http://localhost:8080"
echo -e "${BLUE}Mailpit:${NC}    http://localhost:8025"
echo -e "${BLUE}Database:${NC}   localhost:5432  (user: evmonitor / pass: evmonitor)"
echo ""
echo -e "${RED}Stop: ./stop-dev.sh or Ctrl+C${NC}"
echo ""

cat > stop-dev.sh <<'STOP_SCRIPT'
#!/bin/bash
echo "Stopping EV Monitor Development Environment..."

if [ -f .backend.pid ]; then
    PID=$(cat .backend.pid)
    kill $PID 2>/dev/null && echo "Backend stopped" || true
    rm .backend.pid
fi

if [ -f .frontend.pid ]; then
    PID=$(cat .frontend.pid)
    kill $PID 2>/dev/null && echo "Frontend stopped" || true
    rm .frontend.pid
fi

docker compose -f docker-compose.dev.yml down
echo "All services stopped"
STOP_SCRIPT
chmod +x stop-dev.sh

trap './stop-dev.sh; exit' INT TERM

echo -e "${BLUE}Tailing logs (Ctrl+C to stop all services)...${NC}"
echo ""
tail -f logs/backend.log logs/frontend.log
