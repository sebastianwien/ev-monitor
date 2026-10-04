#!/bin/bash
#
# Gemeinsame Funktionen für backup.sh und predeploy-dump.sh.
# Pfade per Env überschreibbar, damit sich die Skripte lokal testen lassen.
#
BASE_DIR=${BASE_DIR:-/opt/ev-monitor}
APP_DIR=${APP_DIR:-$BASE_DIR/ev-monitor}
COMPOSE_FILE=${COMPOSE_FILE:-$APP_DIR/docker-compose.full.yml}
ENV_FILE=${ENV_FILE:-$APP_DIR/.env}
BACKUP_ROOT=${BACKUP_ROOT:-$BASE_DIR/backup}

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# Liest einen Wert aus .env, ohne die Datei zu sourcen (Sonderzeichen in Secrets).
env_value() {
  grep -m1 "^$1=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | sed -e 's/^["'\'']//' -e 's/["'\'']$//'
}

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

# dump_db <db> <zieldatei>: pg_dump -Fc, danach Prüfung per pg_restore --list.
dump_db() {
  local db=$1 out=$2
  # POSTGRES_USER kommt aus der Container-Umgebung, ist Superuser und darf alle DBs lesen.
  if ! compose exec -T db sh -c "pg_dump -U \"\$POSTGRES_USER\" -Fc '$db'" > "$out"; then
    log "FEHLER: pg_dump $db fehlgeschlagen"; return 1
  fi
  if [ ! -s "$out" ]; then
    log "FEHLER: Dump $db ist leer"; return 1
  fi
  if ! compose exec -T db pg_restore --list < "$out" > /dev/null; then
    log "FEHLER: pg_restore --list für $db fehlgeschlagen"; return 1
  fi
  log "OK: $db ($(du -h "$out" | cut -f1))"
}

# Admin-Alert per SMTP (curl), Zugangsdaten aus .env. Ohne ALERT_EMAIL nur Log.
send_alert() {
  local subject=$1 body=$2
  local to host port user pass from url
  to=$(env_value ALERT_EMAIL)
  host=$(env_value MAIL_HOST)
  if [ -z "$to" ] || [ -z "$host" ]; then
    log "WARNUNG: ALERT_EMAIL oder MAIL_HOST fehlt, kein Alert verschickt"; return 0
  fi
  port=$(env_value MAIL_PORT); port=${port:-587}
  user=$(env_value MAIL_USERNAME)
  pass=$(env_value MAIL_PASSWORD)
  from=$(env_value APP_MAIL_FROM); from=${from:-noreply@ev-monitor.net}
  if [ "$port" = "465" ]; then url="smtps://$host:$port"; else url="smtp://$host:$port"; fi

  printf 'From: %s\r\nTo: %s\r\nSubject: %s\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n%s\r\n' \
    "$from" "$to" "$subject" "$body" \
    | curl -sS --ssl-reqd --url "$url" --user "$user:$pass" \
        --mail-from "$from" --mail-rcpt "$to" --upload-file - \
    || log "WARNUNG: Alert-Mail konnte nicht verschickt werden"
}
