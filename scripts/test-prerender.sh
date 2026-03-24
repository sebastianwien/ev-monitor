#!/bin/bash

# Smoke test: Verifies that bot requests to /modelle are served
# by the prerender service (significantly larger response than normal).
#
# Usage:
#   ./scripts/test-prerender.sh               # tests http://localhost:8081
#   ./scripts/test-prerender.sh https://ev-monitor.net

set -e

BASE_URL="${1:-http://localhost:8081}"
FAILED=0

echo "🤖 Prerender Smoke Test — $BASE_URL"
echo "======================================="

check() {
  local label="$1"
  local ua="$2"
  local expect_prerender="$3"  # "yes" or "no"
  local path="${4:-/modelle}"

  body_size=$(curl -s -A "$ua" -o /dev/null -w "%{size_download}" "$BASE_URL$path")

  if [ "$expect_prerender" = "yes" ]; then
    if [ "$body_size" -gt 10000 ]; then
      echo "✅ $label — ${body_size} bytes (prerendered)"
    else
      echo "❌ $label — ${body_size} bytes (too small, expected prerendered content!)"
      FAILED=$((FAILED + 1))
    fi
  else
    if [ "$body_size" -lt 10000 ]; then
      echo "✅ $label — ${body_size} bytes (normal shell)"
    else
      echo "⚠️  $label — ${body_size} bytes (unexpectedly large for normal user)"
    fi
  fi
}

echo ""
echo "── /modelle (DE) ──────────────────────"
check "Normal User (Chrome)"     "Mozilla/5.0 Chrome/120.0"                         "no"
check "Googlebot"                "Googlebot/2.1"                                    "yes"
check "Bingbot"                  "bingbot/2.0"                                      "yes"
check "PerplexityBot"            "PerplexityBot/1.0"                                "yes"
check "GPTBot"                   "GPTBot/1.0"                                       "yes"
check "ClaudeBot"                "ClaudeBot/1.0"                                    "yes"
check "OAI-SearchBot"            "OAI-SearchBot/1.0"                                "yes"
check "DuckDuckBot"              "DuckDuckBot/1.1"                                  "yes"
check "LinkedInBot"              "LinkedInBot/1.0"                                  "yes"

echo ""
echo "── /en/models (EN) ────────────────────"
check "Normal User (Chrome)"     "Mozilla/5.0 Chrome/120.0"                         "no"   "/en/models"
check "Googlebot"                "Googlebot/2.1"                                    "yes"  "/en/models"
check "GPTBot"                   "GPTBot/1.0"                                       "yes"  "/en/models"
check "ClaudeBot"                "ClaudeBot/1.0"                                    "yes"  "/en/models"

echo ""
echo "── /en/models/tesla/Model_3 (EN detail) ─"
check "Googlebot"                "Googlebot/2.1"                                    "yes"  "/en/models/tesla/Model_3"
check "Normal User (Chrome)"     "Mozilla/5.0 Chrome/120.0"                         "no"   "/en/models/tesla/Model_3"

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ All prerender checks passed."
  exit 0
else
  echo "❌ $FAILED check(s) failed."
  exit 1
fi
