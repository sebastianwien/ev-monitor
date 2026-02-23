# Server Setup Guide - ev-monitor.net

## Prerequisites

- Hetzner Server (Ubuntu 22.04 or newer)
- Domain `ev-monitor.net` pointing to server IP (A-Record)
- SSH access to server

## Step 1: Initial Server Setup

```bash
# Connect to server
ssh -i ~/.ssh/ihle-private root@YOUR_SERVER_IP

# Update system
apt update && apt upgrade -y

# Install Docker
apt install -y docker.io docker-compose-plugin git curl

# Enable Docker service
systemctl enable docker
systemctl start docker

# Add user to docker group (optional, if not root)
usermod -aG docker $USER
```

## Step 2: Configure Firewall

```bash
# Install ufw if not present
apt install -y ufw

# Allow SSH, HTTP, HTTPS
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp

# Enable firewall
ufw --force enable

# Check status
ufw status
```

## Step 3: Clone Repository

```bash
# Create app directory
mkdir -p /opt/ev-monitor
cd /opt/ev-monitor

# Clone repo (replace with your repo URL)
git clone https://github.com/YOUR_USERNAME/ev-monitor.git .

# Verify files
ls -la
```

## Step 4: Configure Environment Variables

```bash
# Copy example env file
cp .env.example .env

# Edit with your secrets
nano .env
```

**IMPORTANT: Generate secure secrets!**

```bash
# Generate JWT secret (64 chars base64)
openssl rand -base64 64

# Generate DB password (32 chars base64)
openssl rand -base64 32
```

**Example `.env` (fill in the CHANGE_ME values):**

```env
DOMAIN=ev-monitor.net
POSTGRES_USER=evmonitor
POSTGRES_PASSWORD=<OUTPUT_FROM_openssl_rand_-base64_32>
POSTGRES_DB=ev_monitor
JWT_SECRET=<OUTPUT_FROM_openssl_rand_-base64_64>
JWT_EXPIRATION_MS=604800000
ALLOWED_ORIGINS=https://ev-monitor.net,https://www.ev-monitor.net
SPRING_PROFILES_ACTIVE=prod
```

## Step 5: Configure SSL Script

```bash
# Edit init-letsencrypt.sh
nano init-letsencrypt.sh
```

Change line 9:
```bash
EMAIL="your-actual-email@example.com"  # Change this!
```

## Step 6: Initial Deployment (HTTP only)

```bash
# Deploy without SSL first (for Let's Encrypt challenge)
./deploy.sh
```

This will:
- Validate your .env file
- Build Docker containers
- Start the application on HTTP (port 80)

**Check if services are running:**
```bash
docker compose ps
docker compose logs -f backend
```

## Step 7: Get SSL Certificate

```bash
# Run Let's Encrypt setup (ONLY ONCE!)
./init-letsencrypt.sh
```

This will:
- Request SSL certificate from Let's Encrypt
- Store cert in Docker volume
- Restart nginx with HTTPS enabled

**Verify SSL:**
```bash
curl -I https://ev-monitor.net
# Should return 200 OK with security headers
```

## Step 8: Verify Deployment

```bash
# Check all services are running
docker compose ps

# Test endpoints
curl https://ev-monitor.net/api/cars/brands
# Should return JSON with car brands

# Check logs
docker compose logs -f backend
docker compose logs -f nginx
```

## Step 9: DNS Configuration

**Add these A-Records to your domain:**

| Type | Name | Value |
|------|------|-------|
| A | @ | YOUR_SERVER_IP |
| A | www | YOUR_SERVER_IP |

**Verify DNS propagation:**
```bash
dig ev-monitor.net +short
dig www.ev-monitor.net +short
# Both should return YOUR_SERVER_IP
```

## Maintenance Commands

### Update Application
```bash
cd /opt/ev-monitor
git pull origin main
./deploy.sh
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend
docker compose logs -f nginx
docker compose logs -f db
```

### Restart Services
```bash
docker compose restart
# or specific service:
docker compose restart backend
```

### Check Disk Space
```bash
df -h
docker system df
```

### Backup Database
```bash
docker compose exec db pg_dump -U evmonitor ev_monitor > backup_$(date +%Y%m%d).sql
```

### Restore Database
```bash
cat backup_20260223.sql | docker compose exec -T db psql -U evmonitor ev_monitor
```

## Security Checklist

- [ ] `.env` file is NOT in git (check with `git status`)
- [ ] Strong passwords generated (32+ characters)
- [ ] JWT secret is random (64+ characters)
- [ ] Firewall enabled (only ports 22, 80, 443)
- [ ] SSL certificate obtained and auto-renewal working
- [ ] ALLOWED_ORIGINS set to production domain only
- [ ] SQL logging disabled in production
- [ ] Regular backups scheduled

## Troubleshooting

### Backend won't start
```bash
# Check backend logs
docker compose logs backend

# Common issues:
# - JWT_SECRET missing → Check .env
# - DB connection failed → Check POSTGRES_PASSWORD
# - Port already in use → netstat -tulpn | grep 8080
```

### Nginx SSL error
```bash
# If certificate not found:
# 1. Ensure DNS points to server
# 2. Run init-letsencrypt.sh again
# 3. Check certbot logs: docker compose logs certbot
```

### Can't connect to database
```bash
# Check DB is running
docker compose ps db

# Check DB logs
docker compose logs db

# Test connection inside container
docker compose exec db psql -U evmonitor -d ev_monitor -c "SELECT 1;"
```

### CORS errors in browser
```bash
# Check ALLOWED_ORIGINS in .env
# Must match EXACTLY the domain you're accessing from
# Example: https://ev-monitor.net (no trailing slash!)
```

## Monitoring

### Check Certificate Expiration
```bash
docker compose exec certbot certbot certificates
```

Certificate will auto-renew via certbot container (runs every 12h).

### Check Memory Usage
```bash
docker stats
```

### Check Application Health
```bash
# HTTP health check (add this to backend later)
curl https://ev-monitor.net/api/cars/brands
```

## Support

- Logs directory: Check `docker compose logs`
- Config files: `/opt/ev-monitor/`
- SSL certs: Docker volume `certbot_etc`
- Database: Docker volume `postgres_data`

---

**Last Updated:** 2026-02-23
