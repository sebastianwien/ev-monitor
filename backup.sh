#!/bin/bash
# EV Monitor - Automated Database Backup
# Setup: sudo crontab -e
#   0 2 * * * /opt/ev-monitor/ev-monitor/backup.sh >> /opt/ev-monitor/logs/backup.log 2>&1

set -e

BACKUP_DIR="/opt/ev-monitor/backups"
RETENTION_DAYS=14
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/ev_monitor_$TIMESTAMP.sql.gz"

mkdir -p "$BACKUP_DIR"
mkdir -p "/opt/ev-monitor/logs"

# Load env vars (DB credentials)
set -a
source /opt/ev-monitor/ev-monitor/.env
set +a

echo "[$TIMESTAMP] Starting backup..."

docker compose -f /opt/ev-monitor/ev-monitor/docker-compose.yml \
  exec -T db \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$BACKUP_FILE"

SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
echo "[$TIMESTAMP] Backup created: $BACKUP_FILE ($SIZE)"

# Delete backups older than RETENTION_DAYS
DELETED=$(find "$BACKUP_DIR" -name "ev_monitor_*.sql.gz" -mtime "+$RETENTION_DAYS" -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
  echo "[$TIMESTAMP] Deleted $DELETED old backup(s) (>${RETENTION_DAYS} days)"
fi

echo "[$TIMESTAMP] Done."
