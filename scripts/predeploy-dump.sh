#!/bin/bash
#
# Dump vor dem Deploy, nur wenn der Deploy neue Flyway-Migrationen mitbringt.
# Aufruf aus den deploy.yml der drei Repos, nach dem Checkout des neuen Stands:
#   predeploy-dump.sh <db> <repo-dir> <alter-commit> <migrationsordner relativ zum repo>
#
# Keine Migrationsänderung: nichts zu tun (Historie liegt im Nacht-Backup).
# Neue Migration: Dump nach $BACKUP_ROOT/predeploy/, die 3 jüngsten je DB bleiben.
# Exit != 0, wenn der Dump scheitert - der Deploy bricht dann ab.
#
set -uo pipefail

source "$(dirname "$(readlink -f "$0")")/backup-lib.sh"

[ $# -eq 4 ] || { echo "Aufruf: $0 <db> <repo-dir> <alter-commit> <migrationsordner>"; exit 2; }
DB=$1 REPO=$2 OLD=$3 MIGRATIONS=$4
KEEP=${KEEP_PREDEPLOY:-3}

if git -C "$REPO" diff --quiet "$OLD" HEAD -- "$MIGRATIONS"; then
  log "Keine neuen Migrationen für $DB seit ${OLD:0:8}, kein Pre-Deploy-Dump"
  exit 0
fi

log "Neue Migrationen für $DB:"
git -C "$REPO" diff --name-only "$OLD" HEAD -- "$MIGRATIONS"

DIR="$BACKUP_ROOT/predeploy"
umask 077
mkdir -p "$DIR"
OUT="$DIR/$DB-$(date +%Y%m%d-%H%M%S).dump"

if ! dump_db "$DB" "$OUT"; then
  rm -f "$OUT"
  send_alert "ev-monitor: Pre-Deploy-Dump $DB fehlgeschlagen" \
    "Deploy abgebrochen, Repo $REPO, Commit $(git -C "$REPO" rev-parse --short HEAD)."
  exit 1
fi

ls -1t "$DIR"/"$DB"-*.dump | tail -n +$((KEEP + 1)) | xargs -r rm -f --
