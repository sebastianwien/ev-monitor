#!/usr/bin/env bash
set -euo pipefail

if [ -z "${STRIPE_SECRET_KEY:-}" ]; then
  echo "Fehler: STRIPE_SECRET_KEY nicht gesetzt." >&2
  exit 1
fi

python3 - <<'EOF'
import os, urllib.request, urllib.parse, json, sys
from datetime import datetime
from collections import defaultdict

KEY = os.environ["STRIPE_SECRET_KEY"]

def stripe_get(path):
    req = urllib.request.Request(
        f"https://api.stripe.com/v1/{path}",
        headers={"Authorization": f"Bearer {KEY}"}
    )
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def stripe_list(path):
    results = []
    url = f"{path}{'&' if '?' in path else '?'}limit=100"
    while url:
        data = stripe_get(url)
        results.extend(data["data"])
        url = f"{path}{'&' if '?' in path else '?'}limit=100&starting_after={results[-1]['id']}" if data.get("has_more") else None
    return results

def fmt_date(ts):
    return datetime.fromtimestamp(ts).strftime("%Y-%m-%d") if ts else "-"

def colored(text, code):
    return f"\033[{code}m{text}\033[0m"

green  = lambda t: colored(t, "32")
yellow = lambda t: colored(t, "33")
red    = lambda t: colored(t, "31")
bold   = lambda t: colored(t, "1")
dim    = lambda t: colored(t, "2")

print()
print(bold("Stripe Report - EV Monitor AutoSync"))
print(bold("=" * 80))

# Subscriptions
subs = stripe_list("subscriptions?status=all&expand[]=data.customer&expand[]=data.latest_invoice")

# Charges
charges_raw = stripe_list("charges?expand[]=data.customer")
customer_charges = defaultdict(list)
for ch in charges_raw:
    cid = ch.get("customer")
    if isinstance(cid, str):
        customer_charges[cid].append(ch)
    elif isinstance(cid, dict):
        customer_charges[cid["id"]].append(ch)

# Balance
balance = stripe_get("balance")
avail = balance["available"][0]["amount"] / 100
pend  = balance["pending"][0]["amount"] / 100

# Stats
active_count   = sum(1 for s in subs if s["status"] == "active")
trial_count    = sum(1 for s in subs if s["status"] == "trialing")
canceled_count = sum(1 for s in subs if s["status"] == "canceled")
paying = [s for s in subs if s["status"] in ("active", "trialing")]

mrr = 0.0
for s in paying:
    price = s["items"]["data"][0]["price"] if s["items"]["data"] else {}
    amount = price.get("unit_amount", 0) / 100
    interval = price.get("recurring", {}).get("interval", "")
    currency = price.get("currency", "eur")
    if currency == "eur":
        mrr += amount if interval == "month" else amount / 12

print()
print(bold("Zusammenfassung"))
print(f"  Aktiv:       {active_count}")
print(f"  Im Trial:    {trial_count}")
print(f"  Storniert:   {canceled_count}")
print(f"  MRR:         {bold(f'{mrr:.2f} EUR/Monat')}")
print(f"  ARR:         {bold(f'{mrr*12:.2f} EUR/Jahr')}")

# Table
print()
print(bold("Alle Kunden"))
col_email   = 38
col_status  = 18
col_produkt = 30
col_paid    = 14
header = (
    f"  {'Email':<{col_email}}"
    f"{'Status':<{col_status}}"
    f"{'Produkt':<{col_produkt}}"
    f"{'Bezahlt':>{col_paid}}"
)
print(dim(header))
print(dim("  " + "-" * (col_email + col_status + col_produkt + col_paid)))

for s in sorted(subs, key=lambda x: x["created"]):
    cust  = s.get("customer", {})
    email = cust.get("email", "?") if isinstance(cust, dict) else "?"
    cid   = cust.get("id") if isinstance(cust, dict) else None
    status = s["status"]

    price    = s["items"]["data"][0]["price"] if s["items"]["data"] else {}
    amount   = price.get("unit_amount", 0) / 100
    interval = price.get("recurring", {}).get("interval", "?")
    currency = price.get("currency", "?").upper()
    plan_label = "Jahrlich" if interval == "year" else "Monatlich"
    produkt  = f"AutoSync {plan_label} ({amount:.2f} {currency})"

    charges   = customer_charges.get(cid, [])
    paid_ok   = [c for c in charges if c.get("paid") and c.get("status") == "succeeded"]
    total_paid = sum(c.get("amount_captured", c.get("amount", 0)) / 100 for c in paid_ok)

    if status == "active":
        status_raw = "✓ Aktiv" + (" (kundigt)" if s.get("cancel_at_period_end") else "")
        status_str = green("✓ Aktiv") + (dim(" (kundigt)") if s.get("cancel_at_period_end") else "")
    elif status == "trialing":
        trial_label = f"~ Trial bis {fmt_date(s['trial_end'])}"
        status_raw = trial_label
        status_str = yellow(trial_label)
    elif status == "canceled":
        status_raw = "✗ Storniert"
        status_str = red("✗ Storniert")
    else:
        status_raw = status
        status_str = status

    # Pad based on visible length
    pad = col_status - len(status_raw)
    status_col = status_str + " " * max(pad, 2)

    paid_str = f"{total_paid:.2f} {currency}"
    paid_colored = bold(paid_str) if total_paid > 0 else dim(paid_str)

    print(
        f"  {email:<{col_email}}"
        f"{status_col}"
        f"{produkt:<{col_produkt}}"
        f"{paid_colored}"
    )

print()
print(bold("Stripe Balance"))
print(f"  Verfugbar (wird ausgezahlt): {bold(f'{avail:.2f} EUR')}")
print(f"  Pending (noch nicht reif):   {bold(f'{pend:.2f} EUR')}")
print(f"  Gesamt in Stripe:            {bold(f'{avail+pend:.2f} EUR')}")
print()
EOF
