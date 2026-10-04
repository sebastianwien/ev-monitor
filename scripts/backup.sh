#!/bin/bash
#
# Nächtliches Backup aller Daten, unabhängig vom Deploy.
# Cron (Nutzer ihle):
#   30 3 * * * /opt/ev-monitor/ev-monitor/scripts/backup.sh >> /opt/ev-monitor/logs/backup/backup.log 2>&1
#
# Ergebnis: $BACKUP_ROOT/YYYY-MM-DD/ mit drei DB-Dumps (-Fc), car_images.tar.gz,
# docker-compose.full.yml, .env und SHA256SUMS. Rechte 700/600.
# Rotation: 7 jüngste Tage + der jüngste Stand je Monat für 6 Monate.
# Restore: docs/deployment/restore.md
#
set -uo pipefail

source "$(dirname "$(readlink -f "$0")")/backup-lib.sh"

DATABASES=(ev_monitor ev_connectors ev_monitor_wallbox)
CAR_IMAGES_VOLUME=${CAR_IMAGES_VOLUME:-ev-monitor_car_images}
KEEP_DAILY=${KEEP_DAILY:-7}
KEEP_MONTHLY=${KEEP_MONTHLY:-6}

exec 9> /tmp/ev-monitor-backup.lock
flock -n 9 || { log "Backup läuft bereits, Abbruch"; exit 1; }

umask 077
DAY=$(date +%F)
TARGET="$BACKUP_ROOT/$DAY"
WORK="$BACKUP_ROOT/.$DAY.tmp"
rm -rf "$WORK"
mkdir -p "$WORK"

log "Backup startet nach $TARGET"
START=$(date +%s)
errors=()

for db in "${DATABASES[@]}"; do
  dump_db "$db" "$WORK/$db.dump" || errors+=("Dump $db")
done

if docker run --rm -v "$CAR_IMAGES_VOLUME:/src:ro" alpine:3.20 tar czf - -C /src . > "$WORK/car_images.tar.gz" \
   && [ -s "$WORK/car_images.tar.gz" ]; then
  log "OK: car_images ($(du -h "$WORK/car_images.tar.gz" | cut -f1))"
else
  errors+=("car_images")
fi

cp "$COMPOSE_FILE" "$WORK/docker-compose.full.yml" || errors+=("docker-compose.full.yml")
cp "$ENV_FILE" "$WORK/env" || errors+=(".env")

(cd "$WORK" && sha256sum -- * > SHA256SUMS)

if [ ${#errors[@]} -eq 0 ]; then
  rm -rf "$TARGET"
  mv "$WORK" "$TARGET"
  log "Backup fertig in $(( $(date +%s) - START )) s, $(du -sh "$TARGET" | cut -f1)"
else
  FAILED="$TARGET-FAILED"
  rm -rf "$FAILED"
  mv "$WORK" "$FAILED"
  log "Backup mit Fehlern: ${errors[*]} (Teilstand in $FAILED)"
  send_alert "ev-monitor: Backup $DAY fehlgeschlagen" \
    "Fehlgeschlagen: ${errors[*]}
Teilstand: $FAILED
Log: /opt/ev-monitor/logs/backup/backup.log"
fi

# Rotation: nur vollständige Tagesordner (YYYY-MM-DD), neueste zuerst.
mapfile -t days < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -regextype posix-extended -regex '.*/[0-9]{4}-[0-9]{2}-[0-9]{2}' -printf '%f\n' | sort -r)
declare -A kept_month
monthly=0
for i in "${!days[@]}"; do
  d=${days[$i]}
  month=${d:0:7}
  if [ "$i" -lt "$KEEP_DAILY" ]; then
    kept_month[$month]=1
    continue
  fi
  if [ -z "${kept_month[$month]:-}" ] && [ "$monthly" -lt "$KEEP_MONTHLY" ]; then
    kept_month[$month]=1
    monthly=$((monthly + 1))
    continue
  fi
  rm -rf -- "${BACKUP_ROOT:?}/$d"
  log "Rotation: $d gelöscht"
done
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '*-FAILED' -mtime +7 -exec rm -rf {} +

[ ${#errors[@]} -eq 0 ]
