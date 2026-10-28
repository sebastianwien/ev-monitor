# Vorschau-Adresse (preview.ev-monitor.net)

Die neue App-Oberfläche (Branch `feat/story-ci`) läuft für eingeladene Fahrer auf einer eigenen Adresse. Gleiches Backend, gleiche Datenbank, gleiche Daten. Prod (`current`) bleibt unberührt.

## Wie es zusammenhängt

- **Frontend:** `preview.yml` (manuell aus dem Branch gestartet) baut nur das Frontend, legt es als Image `ghcr.io/.../ev-monitor-frontend:preview-<sha>` ab und ruft auf dem Server `deploy-release.sh preview <sha>`. Das Skript entpackt nach `/opt/ev-monitor/frontend/releases/preview-<sha>` und setzt den Verweis `preview`. Nie `current`, kein Backend, kein nginx-Neubau. `deploy.yml` nie aus dem Branch starten, das rollt für alle aus.
- **nginx:** eigener Serverblock für `preview.${DOMAIN}` mit eigenem Zertifikat (`/etc/letsencrypt/live/preview.${DOMAIN}/`), Wurzel `/srv/frontend/preview`, `X-Robots-Tag: noindex, nofollow`, API-Proxys wie auf der Hauptadresse, kein Prerender, keine Sitemap. Die Blöcke der Hauptadresse bleiben unverändert. Fehlt das Zertifikat, scheitert `nginx -t` und der Deploy setzt nginx zurück. Die nginx-Änderung wird mit dem nächsten main-Deploy wirksam (Tree-Hash von `nginx/`).
- **Backend:** Branch-Teile müssen vorher additiv nach main (Expand/Contract), das alte Frontend muss damit laufen. Dazu gehört `V217__ui_preview.sql` (Spalte `app_user.ui_preview`, Default false).
- **Zugang:** Merkmal `ui_preview` je Nutzer, im Admin-Tab (Nutzertabelle, Spalte "Vorschau") setzbar, nicht die Rolle `BETA_TESTER`. Steht als Claim `preview` im JWT (wirkt mit dem nächsten Login). Die Vorschau schickt Eingeloggte ohne Merkmal auf dieselbe Seite der Hauptadresse (`previewRedirect` im Router); Admins bleiben immer, weil sie die Einladungen im Admin-Tab der Vorschau setzen. Login und localStorage sind je Adresse getrennt.
- **Rückweg:** auf `/story` steht "Zur gewohnten App" (Hauptadresse). Wer nicht mehr mitmachen will, schreibt kurz, das Merkmal wird entfernt.
- **Messen:** jedes Plausible-Ereignis trägt auf der Vorschau `preview=true`.

## Einrichten (einmalig, auf Prod nur mit Freigabe)

1. DNS: A-Eintrag `preview.ev-monitor.net` auf dieselbe IPv4 wie `ev-monitor.net` (kein AAAA, nginx hört nur auf IPv4).
2. Eigenes Zertifikat nur für `preview.ev-monitor.net` per Webroot über den Certbot-Dienst (`certonly --webroot -w /var/www/certbot -d preview.ev-monitor.net`). Kein `--expand` am Zertifikat der Hauptadresse.
3. Backend-Teile des Branches nach main, Deploy über GitHub Actions (bringt V217 und den nginx-Serverblock). Vorher muss das Zertifikat liegen.
4. `ev-monitor-infra` auf dem Server aktualisieren (`deploy-release.sh preview`).
5. `preview.yml` aus dem Branch starten.
6. Fahrer im Admin-Tab einladen, persönliche Mail (Ich-Form) mit Vorschau-Adresse, Rückweg und Rückmeldeweg.

## Rückweg

Verweis `preview`, den Serverblock und das Zertifikat (`certbot delete --cert-name preview.ev-monitor.net`) entfernen; die Daten bleiben gültig, weil das Backend nur ergänzt wurde.
