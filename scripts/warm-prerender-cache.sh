#!/bin/bash
# Warms the prerender cache by rendering all sitemap URLs upfront.
#
# Usage:
#   ./scripts/warm-prerender-cache.sh              # uses default https://ev-monitor.net
#   ./scripts/warm-prerender-cache.sh http://...   # custom site URL (for local testing)
#
# The script fetches the sitemap, then hits each URL through the prerenderer
# with "Prerender-Recache: 1" — this forces a fresh render even if the URL is
# already cached, while the old cached version remains available until replaced.
#
# Run automatically after each deployment (see deploy.yml).
# Can also be added as a cron job for periodic refresh:
#   0 3 * * * /opt/ev-monitor/ev-monitor/scripts/warm-prerender-cache.sh >> /opt/ev-monitor/ev-monitor/logs/prerender-warm.log 2>&1

set -euo pipefail

SITE_URL="${1:-https://ev-monitor.net}"
PRERENDER_URL="http://127.0.0.1:3000"
DELAY=0.5  # seconds between requests — avoids hammering headless Chrome

echo "🔥 Prerender Cache Warmer"
echo "   Site:       $SITE_URL"
echo "   Prerender:  $PRERENDER_URL"
echo "   Started at: $(date)"
echo ""

# Fetch sitemap and extract all <loc> URLs
SITEMAP="$SITE_URL/sitemap.xml"
echo "📋 Fetching sitemap: $SITEMAP"
URLS=$(curl -sf "$SITEMAP" | grep -oP '(?<=<loc>)[^<]+' | sort -u)

if [ -z "$URLS" ]; then
  echo "❌ No URLs found in sitemap — aborting."
  exit 1
fi

COUNT=$(echo "$URLS" | wc -l | tr -d ' ')
echo "📋 Found $COUNT URLs to warm"
echo ""

WARMED=0
FAILED=0
I=0

while IFS= read -r url; do
  I=$((I + 1))

  STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
    -H "Prerender-Recache: 1" \
    --max-time 30 \
    "${PRERENDER_URL}/${url}" 2>/dev/null || echo "000")

  if [ "$STATUS" = "200" ]; then
    WARMED=$((WARMED + 1))
    echo "  ✅ [$I/$COUNT] $url"
  else
    FAILED=$((FAILED + 1))
    echo "  ⚠️  [$I/$COUNT] HTTP $STATUS — $url"
  fi

  sleep "$DELAY"
done <<< "$URLS"

echo ""
echo "🏁 Done: $WARMED warmed, $FAILED failed — $(date)"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
