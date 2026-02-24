# Manual SSL Setup (Step-by-Step)

Wenn `init-letsencrypt.sh` nicht funktioniert, folge diesen manuellen Schritten:

## Problem: Nginx erwartet SSL-Zertifikat das noch nicht existiert

## Lösung: Dummy-Zertifikat → Echtes Zertifikat

### Step 1: Dummy-Zertifikat im Container erstellen

```bash
# Erstelle temporäres Dummy-Zertifikat
docker compose run --rm --entrypoint "\
  sh -c 'mkdir -p /etc/letsencrypt/live/ev-monitor.net && \
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout /etc/letsencrypt/live/ev-monitor.net/privkey.pem \
    -out /etc/letsencrypt/live/ev-monitor.net/fullchain.pem \
    -subj \"/CN=ev-monitor.net\"'" \
  certbot
```

### Step 2: Nginx starten (mit Dummy-Zertifikat)

```bash
docker compose up -d nginx
docker compose logs nginx  # Check for errors
```

### Step 3: Dummy-Zertifikat löschen

```bash
docker compose run --rm --entrypoint "\
  sh -c 'rm -rf /etc/letsencrypt/live/ev-monitor.net'" \
  certbot
```

### Step 4: Echtes Zertifikat holen

```bash
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  -d ev-monitor.net \
  -d www.ev-monitor.net \
  --email sebastian.wien@posteo.de \
  --agree-tos \
  --no-eff-email \
  --non-interactive
```

### Step 5: Nginx neu laden

```bash
docker compose exec nginx nginx -s reload
```

### Step 6: Verify

```bash
curl -I https://ev-monitor.net
# Should return 200 OK with security headers
```

---

## Alternative: HTTP-Only zuerst

Wenn das nicht funktioniert, ändere temporär die Nginx Config:

### 1. Backup aktuelle Config

```bash
docker compose exec nginx cp /etc/nginx/conf.d/app.conf /etc/nginx/conf.d/app.conf.backup
```

### 2. HTTP-Only Config

```bash
docker compose exec nginx sh -c 'cat > /etc/nginx/conf.d/app.conf <<EOF
server {
    listen 80;
    server_name ev-monitor.net;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        root /usr/share/nginx/html;
        try_files \$uri /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
    }
}
EOF'
```

### 3. Reload Nginx

```bash
docker compose exec nginx nginx -s reload
```

### 4. Hole Zertifikat

```bash
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  -d ev-monitor.net \
  -d www.ev-monitor.net \
  --email sebastian.wien@posteo.de \
  --agree-tos \
  --no-eff-email \
  --non-interactive
```

### 5. Restore HTTPS Config

```bash
docker compose exec nginx cp /etc/nginx/conf.d/app.conf.backup /etc/nginx/conf.d/app.conf
docker compose restart nginx
```

---

## Debugging

### Check DNS

```bash
dig ev-monitor.net +short
# Should return your server IP
```

### Check nginx is serving HTTP

```bash
curl -I http://ev-monitor.net/
# Should return 200 or 301
```

### Check ACME challenge path

```bash
curl http://ev-monitor.net/.well-known/acme-challenge/test
# Should NOT return 404
```

### Check certbot logs

```bash
docker compose logs certbot
```

### Check if certificate exists

```bash
docker compose exec nginx ls -la /etc/letsencrypt/live/
```
