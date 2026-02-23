#!/bin/bash

# EV Monitor - Let's Encrypt SSL Certificate Setup
# Run this ONCE after first deployment to get SSL certificate

set -e

DOMAIN="${DOMAIN:-ev-monitor.net}"
EMAIL="CHANGE_ME@example.com"  # IMPORTANT: Change this to your email!

echo "🔐 Requesting SSL certificate for $DOMAIN..."
echo "📧 Email: $EMAIL"
echo ""

# Validate email is changed
if [[ "$EMAIL" == "CHANGE_ME@example.com" ]]; then
  echo "❌ ERROR: Please change EMAIL in this script to your actual email address!"
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
