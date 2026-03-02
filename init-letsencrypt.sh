#!/bin/bash

# EV Monitor - Let's Encrypt SSL Certificate Setup
# Run this ONCE after first deployment to get SSL certificate

set -e

DOMAIN="${DOMAIN:-ev-monitor.net}"
EMAIL="${CERTBOT_EMAIL:-your@email.com}"  # Set via env var or replace this placeholder

echo "🔐 Requesting SSL certificate for $DOMAIN..."
echo "📧 Email: $EMAIL"
echo ""

# Validate email is changed
if [[ "$EMAIL" == "your@email.com" ]]; then
  echo "❌ ERROR: Please set CERTBOT_EMAIL env var or replace the placeholder email in this script!"
  exit 1
fi

# Request certificate
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  -d "$DOMAIN" \
  -d "www.$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  --non-interactive

echo ""
echo "✅ SSL certificate obtained!"
echo "🔄 Restarting nginx to apply changes..."

docker compose restart nginx

echo ""
echo "🎉 Done! Your site should now be available at:"
echo "   https://$DOMAIN"
echo ""
echo "⚠️  Certificate will auto-renew via certbot container."
