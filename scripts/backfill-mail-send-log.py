#!/usr/bin/env python3
"""
Füllt mail_send_log rückwirkend aus den Backend-Logs (Admin-Tab "Mailings").

Quellen:
- "[MAIL] gesendet|fehlgeschlagen to=[...] subject=..." (LoggingMailSender, ab 19.10.2026): Typ über den Betreff.
- "Sent <...> to user <uuid>" (AppScheduler, ältere Logs): vier Job-Mails mit Nutzer-ID.
Beide Quellen überschneiden sich ab 19.10.2026; Job-Zeilen zählen deshalb nur davor.

Läuft auf dem Server, damit Adressen ihn nicht verlassen:
  python3 backfill-mail-send-log.py /opt/ev-monitor/logs/backend > /tmp/mail_send_log_backfill.sql
Gibt auf stderr nur Zählungen aus. Das SQL ist idempotent und nimmt nur Versand vor dem ersten
Eintrag, den die App selbst geschrieben hat. Zeitstempel bleiben UTC wie in den Logs und in der App.
"""
import gzip
import os
import re
import sys
from collections import Counter

MAIL_LOG_START = "2026-10-19"

# Reihenfolge zählt: spezifischere Betreffs zuerst (VW-Data-Act-Test vor AutoSync-Test).
SUBJECTS = [
    (r"^EV Monitor - (E-Mail bestätigen|Confirm your email)$", "verification"),
    (r"^(Dein Code|Your code): ", "login-code"),
    (r"^EV Monitor - (Passwort zurücksetzen|Reset your password)$", "password-reset"),
    (r"^(Dein Ergebnis für den|Your result for the) ", "personal-range-result"),
    (r"^(Dein VW-Data-Act-AutoSync-Test endet am|Your VW Data Act AutoSync trial ends on) ", "euda-trial-ending"),
    (r"^(VW-Data-Act-AutoSync pausiert|VW Data Act AutoSync paused)", "euda-trial-ended"),
    (r"^(VW-Data-Act-Verbindung unterbrochen|VW Data Act connection interrupted)", "euda-connection-lost"),
    (r"^(Dein Auto meldet jetzt über das VW-Data-Act-Portal|Your car now reports via the VW Data Act portal)", "euda-handover"),
    (r"^(Deine Ladehistorie ist da|Your charging history is here)", "euda-history-imported"),
    (r"^(Dein AutoSync-Test endet am|Your AutoSync trial ends on) ", "trial-ending"),
    (r"^(Kurze Nachricht von mir|A quick note from me)$", "re-engagement"),
    (r"^(Wie läuft AutoSync für dich\?|How is AutoSync working for you\?)$", "autosync-satisfaction"),
    (r"^(Dein Auto war fleißig|Your car's been busy)", "autosync-dormant"),
    (r"^EV Monitor - (Alles bereit für dein erstes Ladetagebuch\?|Ready for your first charging log\?)$", "onboarding-reminder"),
    (r"^(Wie war dein|Dein) \S+ mit (dem|der) |^(How was your|Your) \S+ with the ", "monthly-recap"),
    (r"(wurde nicht veröffentlicht|was not published)$", "rating-review-rejected"),
    (r"( ist jetzt da - leg dein Auto an| is now available - add your car)$", "missing-vehicle-done"),
]
SUBJECTS = [(re.compile(p), k) for p, k in SUBJECTS]

# Mails an den Admin, keine Nutzer-Mailings
ADMIN = re.compile(r"^(Neuer Trial|Neues Abo|Trial umgewandelt|Wochenreport|EV Monitor - Neue Fahrzeug-Meldung|EV Monitor - Ladekarten-Tarife|\[)")

JOB_LINES = [
    (re.compile(r"Sent onboarding reminder to user ([0-9a-f-]{36})"), "onboarding-reminder"),
    (re.compile(r"Sent re-engagement email to user ([0-9a-f-]{36})"), "re-engagement"),
    (re.compile(r"Sent AutoSync satisfaction survey to user ([0-9a-f-]{36})"), "autosync-satisfaction"),
    (re.compile(r"Sent dormant AutoSync email to user ([0-9a-f-]{36})"), "autosync-dormant"),
]

MAIL = re.compile(r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\S* .*\[MAIL\] (gesendet|fehlgeschlagen) to=\[([^\]]*)\] subject="(.*?)"( error=.*)?$')
TS = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})")


def classify(subject):
    for pattern, key in SUBJECTS:
        if pattern.search(subject):
            return key
    return None


def parse(lines, stats):
    """Liefert (key, email, user_id, sent, ts) je Versand, ohne Doppelte."""
    seen = set()
    for line in lines:
        line = line.rstrip("\n")
        m = MAIL.match(line)
        if m:
            ts, status, to, subject = m.group(1), m.group(2), m.group(3), m.group(4)
            key = classify(subject)
            if key is None:
                stats["admin" if ADMIN.search(subject) else "unbekannter Betreff"] += 1
                continue
            email = to.split(",")[0].strip()
            row = (key, email.lower(), None, status == "gesendet", ts.replace("T", " "))
        else:
            row = None
            for pattern, key in JOB_LINES:
                j = pattern.search(line)
                t = TS.match(line)
                if j and t and t.group(1)[:10] < MAIL_LOG_START:
                    row = (key, None, j.group(1), True, t.group(1).replace("T", " "))
                    break
            if row is None:
                continue
        if row in seen:
            stats["doppelt"] += 1
            continue
        seen.add(row)
        stats[row[0]] += 1
        yield row


def read_logs(directory):
    for name in sorted(os.listdir(directory)):
        path = os.path.join(directory, name)
        if not os.path.isfile(path):
            continue
        opener = gzip.open if name.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8", errors="replace") as f:
            yield from f


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    return "'" + str(value).replace("'", "''") + "'"


def to_sql(rows):
    values = ",\n".join("(" + ", ".join(sql_literal(v) for v in r) + ")" for r in rows)
    if not values:
        return "-- nichts zu tun\n"
    return f"""BEGIN;
INSERT INTO mail_send_log (mail_type, user_id, sent, sent_at)
SELECT v.mail_type, coalesce(v.user_id::uuid, u.id), v.sent, v.sent_at::timestamp
FROM (VALUES
{values}
) AS v(mail_type, email, user_id, sent, sent_at)
LEFT JOIN app_user u ON v.email IS NOT NULL AND lower(u.email) = v.email
WHERE v.sent_at::timestamp < coalesce((SELECT min(sent_at) FROM mail_send_log), 'infinity'::timestamp)
  AND (v.user_id IS NULL OR EXISTS (SELECT 1 FROM app_user a WHERE a.id = v.user_id::uuid))
  AND v.sent_at::timestamp > now() - interval '365 days';
COMMIT;
"""


def main():
    stats = Counter()
    rows = list(parse(read_logs(sys.argv[1]), stats))
    sys.stdout.write(to_sql(rows))
    for key, n in sorted(stats.items(), key=lambda kv: -kv[1]):
        print(f"{n:6d}  {key}", file=sys.stderr)
    if rows:
        print(f"Zeitraum: {min(r[4] for r in rows)} bis {max(r[4] for r in rows)}", file=sys.stderr)


if __name__ == "__main__":
    main()
