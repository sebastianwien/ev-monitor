# Build-Modus fuer die native Capacitor-App (iOS/Android).
# Die App laeuft unter capacitor://localhost - relative /api-Calls greifen
# daher nicht, die absolute Prod-URL ist Pflicht.
# Lokales Backend-Testen: .env.app.local anlegen (gitignored) und ueberschreiben.
VITE_API_BASE_URL=https://ev-monitor.net/api
