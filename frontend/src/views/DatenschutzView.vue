<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon } from '@heroicons/vue/24/outline'

const email = computed(() => ['impressum', 'ev-monitor.net'].join('@'))
</script>

<template>
  <div class="min-h-screen bg-white">
    <!-- Navbar -->
    <nav class="border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-2">
            <BoltIcon class="h-8 w-8 text-green-600" />
            <span class="text-xl font-bold text-gray-900">EV Monitor</span>
          </router-link>
          <router-link to="/modelle" class="text-sm font-medium text-gray-600 hover:text-green-700 transition">
            Modelle
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      <h1 class="text-4xl font-bold text-gray-900 mb-8">Datenschutzerklärung</h1>

      <div class="prose prose-lg text-gray-600 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">1. Verantwortlicher</h2>
          <p>
            Verantwortlich für die Datenverarbeitung auf dieser Website ist:<br />
            Sebastian Wien<br />
            Cotheniusstrasse 5, 10407 Berlin<br />
            E-Mail: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">2. Erhebung und Speicherung personenbezogener Daten</h2>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">2.1 Registrierung</h3>
          <p>
            Zur Nutzung unseres Services ist eine Registrierung erforderlich. Dabei werden folgende Daten erhoben:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>E-Mail-Adresse (für Login und E-Mail-Verifizierung)</li>
            <li>Benutzername (für die Anzeige im System)</li>
            <li>Passwort (verschlüsselt mit BCrypt)</li>
          </ul>
          <p class="mt-4">
            Die E-Mail-Verifizierung ist verpflichtend. Wir senden einen Verifizierungslink an die angegebene E-Mail-Adresse.
            Der Token ist 24 Stunden gültig.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">2.2 Ladevorgänge</h3>
          <p>
            Beim Tracken von Ladevorgängen speichern wir:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Geladene Energie (kWh)</li>
            <li>Kosten (EUR)</li>
            <li>Ladedauer (Minuten)</li>
            <li>Odometer-Stand (optional)</li>
            <li>Zeitstempel</li>
          </ul>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">2.3 Standortdaten (Privacy-First)</h3>
          <p>
            <strong>WICHTIG:</strong> Wir speichern KEINE exakten GPS-Koordinaten!
          </p>
          <p>
            Stattdessen verwenden wir <strong>Geohashing</strong> mit 5 Zeichen Präzision (~5km Radius).
            Wenn Sie einen Standort eingeben, wird dieser in einen anonymisierten Geohash umgewandelt.
            Ihre exakte Position kann daraus nicht rekonstruiert werden.
          </p>
          <p class="mt-4">
            <strong>Beispiel:</strong> Standort "Berlin Mitte" wird zu Geohash "u33d1" (deckt ~5km Gebiet ab)
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">2.4 Tesla Import (optional)</h3>
          <p>
            Wenn Sie die Tesla-Integration nutzen, speichern wir:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Tesla Access Token (AES-verschlüsselt in Datenbank)</li>
            <li>Tesla Refresh Token (AES-verschlüsselt)</li>
            <li>Fahrzeug-ID</li>
          </ul>
          <p class="mt-4">
            Die Token werden mit AES-256-GCM verschlüsselt und sind nur für Ihren Account zugänglich.
            Sie können die Verbindung jederzeit trennen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">3. Zweck der Datenverarbeitung</h2>
          <p>Die erhobenen Daten werden verwendet für:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Bereitstellung und Verbesserung des Services</li>
            <li>Anzeige persönlicher Statistiken (nur für Sie sichtbar)</li>
            <li>Berechnung anonymisierter Community-Statistiken</li>
            <li>WLTP-Vergleiche (nur aggregierte Daten)</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">4. Community-Daten & Anonymisierung</h2>
          <p>
            Ihre Ladevorgänge können in <strong>anonymisierten</strong> Community-Statistiken einfließen:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Durchschnittliche Kosten pro kWh für ein Fahrzeugmodell</li>
            <li>Durchschnittlicher Verbrauch pro 100km</li>
            <li>Anzahl der Ladevorgänge pro Modell</li>
          </ul>
          <p class="mt-4">
            <strong>Wichtig:</strong> Es werden KEINE persönlichen Daten oder Nutzernamen in öffentlichen Statistiken angezeigt.
            Alle Daten sind aggregiert und anonymisiert.
          </p>
          <p class="mt-4">
            Sie können Ihre Daten als "Seed Data" markieren lassen, um sie von öffentlichen Statistiken auszuschließen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">5. Cookies & Tracking</h2>
          <p>
            Wir verwenden <strong>keine Tracking-Cookies</strong> von Drittanbietern.
          </p>
          <p class="mt-4">
            Unsere Website nutzt ausschließlich:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Session-Token (JWT):</strong> Im localStorage gespeichert für Authentifizierung (7 Tage Gültigkeit)</li>
            <li><strong>Keine Cookies von Drittanbietern</strong> — kein Google Analytics, kein Facebook Pixel</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">5a. Webanalyse (Plausible Analytics)</h2>
          <p>
            Wir nutzen <strong>Plausible Analytics</strong>, einen datenschutzfreundlichen Webanalysedienst der Plausible Insights OÜ (Tartu, Estland — EU).
          </p>
          <p class="mt-4">
            Plausible verwendet <strong>keine Cookies</strong> und erhebt <strong>keine personenbezogenen Daten</strong>.
            Es wird kein Fingerprinting eingesetzt und kein geräteübergreifendes Tracking durchgeführt.
          </p>
          <p class="mt-4">Folgende anonymisierte Daten werden erfasst:</p>
          <ul class="list-disc list-inside space-y-2 mt-2">
            <li>Aufgerufene Seiten (aggregiert)</li>
            <li>Herkunft des Besuchs (z.B. Suchmaschine, direkter Aufruf)</li>
            <li>Land (wird aus der IP-Adresse abgeleitet; die IP-Adresse selbst wird nicht gespeichert)</li>
            <li>Gerätetyp (Desktop / Mobil)</li>
            <li>Anonymisierte Nutzungsaktionen (z.B. Registrierung abgeschlossen, Ladevorgang erfasst)</li>
          </ul>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Berechtigtes Interesse gemäß Art. 6 Abs. 1 lit. f DSGVO.
            Da keine personenbezogenen Daten erhoben werden, ist kein Cookie-Banner erforderlich.
          </p>
          <p class="mt-4">
            Weitere Informationen: <a href="https://plausible.io/data-policy" class="text-indigo-600 hover:underline" target="_blank" rel="noopener">plausible.io/data-policy</a>
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">6. Weitergabe von Daten</h2>
          <p>
            Wir geben Ihre Daten <strong>nicht</strong> an Dritte weiter, außer:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Es besteht eine gesetzliche Verpflichtung</li>
            <li>Sie haben explizit zugestimmt</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">7. Ihre Rechte (DSGVO)</h2>
          <p>Sie haben das Recht auf:</p>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Auskunft:</strong> Welche Daten wir über Sie speichern</li>
            <li><strong>Berichtigung:</strong> Korrektur falscher Daten</li>
            <li><strong>Löschung:</strong> Löschung Ihres Accounts und aller zugehörigen Daten</li>
            <li><strong>Datenportabilität:</strong> Export Ihrer Daten im JSON-Format</li>
            <li><strong>Widerspruch:</strong> Widerspruch gegen die Verarbeitung</li>
          </ul>
          <p class="mt-4">
            Zur Ausübung Ihrer Rechte kontaktieren Sie uns unter:
            <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">8. Datensicherheit</h2>
          <p>Wir setzen folgende Sicherheitsmaßnahmen ein:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>HTTPS/TLS-Verschlüsselung für alle Verbindungen</li>
            <li>BCrypt-Hashing für Passwörter (Strength 10)</li>
            <li>AES-256-GCM Verschlüsselung für sensible Tokens</li>
            <li>JWT mit Signatur-Validierung</li>
            <li>Regelmäßige Security-Updates</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">9. Speicherdauer</h2>
          <p>
            Ihre Daten werden gespeichert, solange Ihr Account aktiv ist.
          </p>
          <p class="mt-4">
            Bei Account-Löschung werden alle personenbezogenen Daten unwiderruflich gelöscht.
            Anonymisierte Statistiken bleiben bestehen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">10. Externe Dienste: Ko-fi</h2>
          <p>
            Auf unserer Website befindet sich ein Link zur Plattform Ko-fi
            (<a href="https://ko-fi.com" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">ko-fi.com</a>),
            über die Sie die Entwicklung von EV Monitor freiwillig unterstützen können.
          </p>
          <p class="mt-4">
            Der Link öffnet die externe Website von Ko-fi. EV Monitor bettet keinen Ko-fi-Code oder externe Scripts ein.
            Es werden durch den Klick auf den Link keine Daten von uns an Ko-fi übertragen.
            Erst wenn Sie die Ko-fi-Website besuchen, gelten deren Datenschutzbestimmungen:
            <a href="https://more.ko-fi.com/privacy" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">https://more.ko-fi.com/privacy</a>.
          </p>
          <p class="mt-4">
            Die Nutzung von Ko-fi ist vollständig freiwillig und hat keinen Einfluss auf die Nutzung von EV Monitor.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">11. Änderungen dieser Datenschutzerklärung</h2>
          <p>
            Wir behalten uns vor, diese Datenschutzerklärung anzupassen, um sie an geänderte Rechtslagen
            oder Änderungen des Services anzupassen.
          </p>
          <p class="mt-4">
            Stand: {{ new Date().toLocaleDateString('de-DE', { year: 'numeric', month: 'long' }) }}
          </p>
        </section>
      </div>

      <div class="mt-12 pt-8 border-t border-gray-200">
        <router-link
          to="/"
          class="text-green-600 hover:text-green-700 font-medium"
        >
          ← Zurück zur Startseite
        </router-link>
      </div>
    </div>
  </div>
</template>
