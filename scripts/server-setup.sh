#!/bin/bash
#
# Einmaliges Server-Setup für ev-monitor auf Prod (idempotent, erneut ausführbar).
# Richtet ein, was früher bei jedem Deploy lief: Docker-Cleanup-Skript und Cron.
#
# Aufruf auf dem Server: bash /opt/ev-monitor/ev-monitor/scripts/server-setup.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLEANUP_SCRIPT="/opt/docker-cleanup.sh"

echo "🧹 Docker-Cleanup nach $CLEANUP_SCRIPT kopieren..."
sudo install -m 755 "$SCRIPT_DIR/docker-cleanup.sh" "$CLEANUP_SCRIPT"

# Root-Crontab: täglich 3 und 15 Uhr (Stand Prod 04.10.2026)
CRON_JOB="0 3,15 * * * $CLEANUP_SCRIPT >> /var/log/docker-cleanup.log 2>&1"
if sudo crontab -l 2>/dev/null | grep -qF "$CLEANUP_SCRIPT"; then
  echo "✅ Cleanup-Cron schon vorhanden: $(sudo crontab -l | grep -F "$CLEANUP_SCRIPT")"
else
  (sudo crontab -l 2>/dev/null; echo "$CRON_JOB") | sudo crontab -
  echo "✅ Cleanup-Cron eingerichtet"
fi
