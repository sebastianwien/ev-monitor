#!/bin/bash

# EV Monitor - Let's Encrypt SSL Certificate Setup
# Based on: https://github.com/wmnnd/nginx-certbot

set -e

DOMAIN="${DOMAIN:-ev-monitor.net}"
EMAIL="sebastian.wien@posteo.de"
STAGING=0  # Set to 1 for testing (avoids rate limits)

echo "🔐 Let's Encrypt SSL Setup"
echo "=========================="
echo "Domain: $DOMAIN"
echo "Email: $EMAIL"
echo ""

# Check if certificate already exists
if [ -d "/etc/letsencrypt/live/$DOMAIN" ]; then
  echo "⚠️  Certificate for $DOMAIN already exists!"
  read -p "Remove and recreate? (y/N): " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Removing old certificate..."
    sudo rm -rf "/etc/letsencrypt/live/$DOMAIN"
    sudo rm -rf "/etc/letsencrypt/archive/$DOMAIN"
    sudo rm -f "/etc/letsencrypt/renewal/$DOMAIN.conf"
  else
    echo "Exiting."
    exit 0
  fi
fi

# Create dummy certificate to bootstrap nginx
echo "📝 Creating dummy certificate for $DOMAIN..."
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
sudo mkdir -p "$CERT_PATH"
sudo openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout "$CERT_PATH/privkey.pem" \
  -out "$CERT_PATH/fullchain.pem" \
  -subj "/CN=$DOMAIN" > /dev/null 2>&1

echo "✅ Dummy certificate created"

# Start nginx with dummy certificate
echo "🔄 Starting nginx..."
docker compose up -d nginx
sleep 3

# Delete dummy certificate
echo "🗑️  Removing dummy certificate..."
sudo rm -rf "/etc/letsencrypt/live/$DOMAIN"

# Request real certificate
echo ""
echo "🔐 Requesting real certificate from Let's Encrypt..."
echo "   (This may take 30-60 seconds...)"
echo ""

if [ $STAGING != "0" ]; then
  STAGING_ARG="--staging"
  echo "⚠️  Using STAGING environment (for testing)"
else
  STAGING_ARG=""
fi

docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  $STAGING_ARG \
  -d "$DOMAIN" \
  -d "www.$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  --non-interactive

CERT_EXIT_CODE=$?

if [ $CERT_EXIT_CODE -ne 0 ]; then
  echo ""
  echo "❌ Certificate request FAILED!"
  echo ""
  echo "Common issues:"
  echo ""
  echo "1. DNS not configured:"
  echo "   → Run: dig $DOMAIN +short"
  echo "   → Should return: $(curl -s ifconfig.me 2>/dev/null || echo 'YOUR_SERVER_IP')"
  echo ""
  echo "2. Firewall blocking port 80:"
  echo "   → Run: sudo ufw status"
  echo "   → Ensure: 80/tcp is ALLOW"
  echo ""
  echo "3. Port 80 not reachable:"
  echo "   → Test: curl http://$DOMAIN/"
  echo ""
  echo "Debug logs:"
  echo "   docker compose logs certbot"
  echo "   docker compose logs nginx"
  echo ""
  exit 1
fi

echo ""
echo "✅ Real certificate obtained!"

# Reload nginx to use real certificate
echo "🔄 Reloading nginx with real certificate..."
docker compose exec nginx nginx -s reload

echo ""
echo "🎉 SSL Setup Complete!"
echo ""
echo "✅ Your site is now available at:"
echo "   https://$DOMAIN"
echo "   https://www.$DOMAIN"
echo ""
echo "🔄 Certificate auto-renews every 60 days (certbot checks every 12h)"
echo ""
echo "📊 Verify SSL:"
echo "   curl -I https://$DOMAIN"
echo "   https://www.ssllabs.com/ssltest/analyze.html?d=$DOMAIN"
echo ""
