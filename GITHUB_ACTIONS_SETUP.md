# GitHub Actions CI/CD Setup

## Required GitHub Secrets

Navigate to: **Settings → Secrets and variables → Actions → New repository secret**

Add the following secrets:

### 1. SERVER_HOST
```
46.225.210.231
```
(Your Hetzner server IP address)

### 2. SERVER_USER
```
ihle
```
(SSH user on the server)

### 3. SSH_PRIVATE_KEY
```
<your-private-key-content>
```

**How to get your private SSH key:**

```bash
# On your local machine (the one you use to SSH into the server)
cat ~/.ssh/id_rsa
# OR if you use a different key:
cat ~/.ssh/id_ed25519
```

Copy the **entire output** including `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----`.

**⚠️ IMPORTANT:** Never commit this key to the repository!

---

## Workflow Overview

The GitHub Actions workflow (`deploy.yml`) triggers on:
- Every push to `main` branch
- Manual trigger via GitHub UI

### Workflow Steps

1. **Test Job**:
   - Checkout code
   - Set up JDK 21
   - Run backend tests (`./gradlew test`)
   - Set up Node.js 20
   - Install frontend dependencies
   - Build frontend

2. **Deploy Job** (only if tests pass):
   - SSH into Hetzner server
   - Pull latest changes from `main`
   - Create database backup
   - Run `deploy.sh`
   - Verify deployment

3. **Health Check**:
   - Wait 30 seconds
   - Test `https://ev-monitor.net` responds with HTTP 200

4. **Flyway Verification**:
   - Check backend logs for Flyway migration success

---

## Testing the Workflow

### Local Testing (without deploying)

```bash
# Backend tests
cd backend
./gradlew test

# Frontend build
cd frontend
npm install
npm run build
```

### Manual Trigger

1. Go to **GitHub → Actions tab**
2. Select **"Deploy to Production"**
3. Click **"Run workflow"**
4. Choose `main` branch
5. Click **"Run workflow"** button

---

## Monitoring Deployments

### View Workflow Runs
- **GitHub → Actions tab** - See all workflow runs and logs

### View Server Logs
```bash
ssh ihle@46.225.210.231
cd /opt/ev-monitor/ev-monitor
docker compose logs -f backend
```

### Check Flyway Migration History
```bash
ssh ihle@46.225.210.231
cd /opt/ev-monitor/ev-monitor
docker compose exec db psql -U evmonitor -d ev_monitor -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## Rollback Strategy

If a deployment fails:

1. **Check logs**:
   ```bash
   docker compose logs backend
   ```

2. **Rollback code**:
   ```bash
   git revert HEAD
   git push origin main  # Triggers new deployment
   ```

3. **Rollback database** (if migration failed):
   ```bash
   # Restore from backup
   docker compose exec -T db psql -U evmonitor -d ev_monitor < backup-YYYYMMDD-HHMMSS.sql
   ```

4. **Manual Flyway repair** (if migration partially applied):
   ```bash
   # SSH into server
   cd /opt/ev-monitor/ev-monitor
   docker compose exec db psql -U evmonitor -d ev_monitor

   # Check failed migrations
   SELECT * FROM flyway_schema_history WHERE success = false;

   # Delete failed entry (CAREFUL!)
   DELETE FROM flyway_schema_history WHERE version = 'X' AND success = false;
   ```

---

## Troubleshooting

### Deployment fails with "Permission denied (publickey)"
- Check if `SSH_PRIVATE_KEY` secret is correctly set
- Verify key format (must include header/footer)
- Test SSH connection manually: `ssh -i ~/.ssh/id_rsa ihle@46.225.210.231`

### Health check fails
- Check if nginx is running: `docker compose ps`
- Check SSL certificate: `docker compose logs nginx`
- Verify DNS: `nslookup ev-monitor.net`

### Flyway migration fails
- Check `docker compose logs backend` for SQL errors
- Verify `.env` file has correct DB credentials
- Check if schema changes conflict with existing data

---

## Security Best Practices

- ✅ Never commit secrets to the repository
- ✅ Use GitHub Secrets for sensitive data
- ✅ Regularly rotate SSH keys
- ✅ Monitor failed login attempts on server
- ✅ Keep database backups for at least 30 days
- ✅ Test deployments on staging environment first (when available)

---

## Next Steps

- [ ] Set up GitHub Secrets (see above)
- [ ] Test workflow with a small commit
- [ ] Monitor first automated deployment
- [ ] Verify Flyway migrations applied correctly
- [ ] Document any issues in `IDEAS.md`
