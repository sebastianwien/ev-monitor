#!/bin/bash
#
# Rotiert die pg_dump-Backups aus dem Deploy (backup-YYYYMMDD-HHMMSS.sql[.gz]).
# Behalten werden: die 7 juengsten Dumps + der juengste Dump jedes Monats.
# Aufruf aus dem Deploy-Workflow nach dem Anlegen des neuen Backups.
#
set -euo pipefail

KEEP_RECENT=${KEEP_RECENT:-7}
cd "${1:-.}"

# Neueste zuerst; Dateiname traegt den Zeitstempel, daher reicht Sortierung nach Name.
mapfile -t backups < <(ls -1 backup-*.sql backup-*.sql.gz 2>/dev/null | sort -r)
[ "${#backups[@]}" -gt "$KEEP_RECENT" ] || exit 0

declare -A keep_month
deleted=0
for i in "${!backups[@]}"; do
  f="${backups[$i]}"
  month="${f:7:6}"   # backup-YYYYMM...
  if [ "$i" -lt "$KEEP_RECENT" ] || [ -z "${keep_month[$month]:-}" ]; then
    keep_month[$month]=1
    continue
  fi
  rm -f -- "$f"
  deleted=$((deleted + 1))
done
echo "Backup-Rotation: ${#backups[@]} gefunden, $deleted geloescht, $((${#backups[@]} - deleted)) behalten"
