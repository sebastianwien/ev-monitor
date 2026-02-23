# Deployment Checklist - Production Ready

## ✅ Completed Changes (Phase 1-4)

### Phase 1: Environment Variables & Secrets
- ✅ `.env.example` created with all required variables
- ✅ `.gitignore` updated (root + backend)
- ✅ `JwtService.java` - removed hardcoded fallback
- ✅ `application.yml` - SQL logging disabled, JWT config added
- ✅ `docker-compose.yml` - all env vars required, no fallbacks

### Phase 2: CORS Configuration
- ✅ `SecurityConfig.java` - dynamic CORS from `ALLOWED_ORIGINS` env var
- ✅ `CarController.java` - removed `@CrossOrigin(origins = "*")`
- ✅ `EvLogController.java` - removed `@CrossOrigin(origins = "*")`
- ✅ `CoinLogController.java` - removed `@CrossOrigin(origins = "*")`
- ✅ `VehicleSpecificationController.java` - removed hardcoded localhost

### Phase 3: SSL/HTTPS in Nginx
- ✅ `nginx/conf.d/app.conf.template` - HTTPS config with security headers
- ✅ `init-letsencrypt.sh` - Let's Encrypt setup script

### Phase 4: Deployment Script
- ✅ `deploy.sh` - validation & deployment automation
- ✅ `SERVER_SETUP.md` - comprehensive server setup guide

### Phase 5: Server Deployment (TODO - Manual)
- ⏳ **YOU ARE HERE** - See instructions below

---

## 🚀 Next Steps: Deploy to Hetzner

### Quick Start (5 Minutes)

1. **Connect to Server:**
   ```bash
   ssh -i ~/.ssh/ihle-private root@YOUR_HETZNER_IP
   ```

2. **Setup Environment:**
   ```bash
   # Install Docker
   apt update && apt install -y docker.io docker-compose-plugin git
   systemctl enable --now docker

   # Configure Firewall
   apt install -y ufw
   ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp
   ufw --force enable
   ```

3. **Clone & Configure:**
   ```bash
   mkdir -p /opt/ev-monitor && cd /opt/ev-monitor
   git clone YOUR_REPO_URL .
   cp .env.example .env
   nano .env  # Fill in secrets (see below)
   ```

4. **Generate Secrets:**
   ```bash
   echo "JWT_SECRET=$(openssl rand -base64 64)"
   echo "POSTGRES_PASSWORD=$(openssl rand -base64 32)"
   # Copy these into .env!
   ```

5. **Deploy:**
   ```bash
   ./deploy.sh
   ```

6. **Get SSL Certificate:**
   ```bash
   nano init-letsencrypt.sh  # Change EMAIL line
   ./init-letsencrypt.sh
   ```

7. **Verify:**
   ```bash
   curl -I https://ev-monitor.net
   docker compose ps
   ```

---

## 🔐 Critical .env Variables

**Required (must change):**
```env
DOMAIN=ev-monitor.net
POSTGRES_PASSWORD=<RANDOM_32_CHARS>
JWT_SECRET=<RANDOM_64_CHARS>
ALLOWED_ORIGINS=https://ev-monitor.net,https://www.ev-monitor.net
```

**Optional (defaults OK for dev):**
```env
POSTGRES_USER=evmonitor
POSTGRES_DB=ev_monitor
JWT_EXPIRATION_MS=604800000
SPRING_PROFILES_ACTIVE=prod
```

---

## 🛡️ Security Verification

After deployment, verify:

```bash
# 1. No hardcoded secrets
grep -r "SuperSecretPassword" /opt/ev-monitor || echo "✅ No hardcoded passwords"

# 2. .env not in git
cd /opt/ev-monitor && git status | grep ".env" && echo "❌ .env is tracked!" || echo "✅ .env not tracked"

# 3. HTTPS working
curl -I https://ev-monitor.net | grep -i "strict-transport-security" && echo "✅ HSTS enabled"

# 4. CORS restricted
curl -H "Origin: https://malicious-site.com" https://ev-monitor.net/api/cars/brands -I | grep -i "access-control-allow-origin" && echo "❌ CORS too permissive!" || echo "✅ CORS restricted"

# 5. SQL logging off
docker compose logs backend | grep -i "select" && echo "⚠️ SQL logging active" || echo "✅ SQL logging disabled"
```

---

## 📊 Monitoring Commands

```bash
# Service status
docker compose ps

# Live logs
docker compose logs -f backend

# Resource usage
docker stats

# Certificate expiration
docker compose exec certbot certbot certificates

# Database backup
docker compose exec db pg_dump -U evmonitor ev_monitor > backup_$(date +%Y%m%d).sql
```

---

## 🐛 Troubleshooting

### App won't start
```bash
# Check .env is loaded
docker compose config | grep JWT_SECRET

# Check backend logs
docker compose logs backend | tail -50
```

### SSL certificate fails
```bash
# Ensure DNS is correct
dig ev-monitor.net +short  # Should return server IP

# Check certbot logs
docker compose logs certbot

# Retry manually
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  -d ev-monitor.net -d www.ev-monitor.net \
  --email YOUR_EMAIL --agree-tos
```

### CORS errors
```bash
# Verify ALLOWED_ORIGINS in container
docker compose exec backend env | grep ALLOWED_ORIGINS

# Should match exactly: https://ev-monitor.net (no trailing slash!)
```

---

## 📝 Files Modified

### Created:
- `.env.example`
- `.gitignore`
- `init-letsencrypt.sh`
- `deploy.sh`
- `SERVER_SETUP.md` (detailed guide)
- `DEPLOYMENT_CHECKLIST.md` (this file)

### Modified:
- `backend/.gitignore`
- `backend/src/main/java/com/evmonitor/infrastructure/security/JwtService.java`
- `backend/src/main/java/com/evmonitor/infrastructure/security/SecurityConfig.java`
- `backend/src/main/java/com/evmonitor/infrastructure/web/CarController.java`
- `backend/src/main/java/com/evmonitor/infrastructure/web/EvLogController.java`
- `backend/src/main/java/com/evmonitor/infrastructure/web/CoinLogController.java`
- `backend/src/main/java/com/evmonitor/infrastructure/web/VehicleSpecificationController.java`
- `backend/src/main/resources/application.yml`
- `docker-compose.yml`
- `nginx/conf.d/app.conf.template`

---

## ✅ Pre-Deployment Checklist

Before running `./deploy.sh`:

- [ ] DNS A-Record set (`ev-monitor.net` → server IP)
- [ ] `.env` file created and filled
- [ ] JWT_SECRET is random 64+ chars
- [ ] POSTGRES_PASSWORD is random 32+ chars
- [ ] ALLOWED_ORIGINS set to production domain
- [ ] `init-letsencrypt.sh` EMAIL changed
- [ ] Firewall configured (ports 22, 80, 443)
- [ ] Docker installed on server
- [ ] Git repo accessible from server

---

**Implementation Time:** ~55 minutes
**Last Updated:** 2026-02-23
