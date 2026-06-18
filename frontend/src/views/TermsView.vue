<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon } from '@heroicons/vue/24/outline'

const email = computed(() => ['impressum', 'ev-monitor.net'].join('@'))
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- Navbar -->
    <nav class="border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-2">
            <BoltIcon class="h-8 w-8 text-green-600" />
            <span class="text-xl font-bold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </router-link>
          <router-link to="/modelle" class="text-sm font-medium text-gray-600 dark:text-gray-400 hover:text-green-700 transition">
            Modelle
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      <h1 class="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-8">Nutzungsbedingungen</h1>

      <div class="prose prose-lg text-gray-600 dark:text-gray-400 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">1. Geltungsbereich &amp; Leistung</h2>
          <p>
            EV Monitor ist ein Dienst zum Erfassen und Auswerten von Ladevorgängen und Verbrauch von
            Elektrofahrzeugen, nutzbar über Website und App. Der Kernfunktionsumfang ist kostenlos; einzelne
            Zusatzfunktionen sind kostenpflichtig (siehe Abschnitt 6). Die Nutzung erfolgt freiwillig. Wir
            behalten uns vor, den Dienst weiterzuentwickeln, zu ändern oder einzustellen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">2. Registrierung &amp; Nutzerverantwortung</h2>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Account:</strong> Für die Nutzung ist eine Registrierung mit E-Mail-Adresse und Passwort nötig; die E-Mail-Adresse muss verifiziert werden.</li>
            <li><strong>Datensorgfalt:</strong> Nutzer sind für die Korrektheit ihrer Eingaben (kWh, Kosten, Standort, Fahrzeug) selbst verantwortlich.</li>
            <li><strong>Keine Gewähr für Auswertungen:</strong> Verbrauchsstatistiken und WLTP-Vergleiche dienen nur zu Informationszwecken; eine Gewähr für ihre Richtigkeit übernehmen wir nicht.</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">3. Datenschutz</h2>
          <p>
            Wie wir personenbezogene Daten verarbeiten, steht in unserer
            <router-link to="/datenschutz" class="text-green-600 hover:text-green-700">Datenschutzerklärung</router-link>.
            Standortdaten werden ausschließlich anonymisiert (Geohash) gespeichert, nie als exakte GPS-Koordinaten.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">4. Öffentliche Statistiken</h2>
          <p>
            Für jedes Fahrzeugmodell zeigen wir anonymisierte, aggregierte Statistiken öffentlich an
            (z. B. durchschnittlicher Verbrauch, Kosten pro kWh, Anzahl Ladevorgänge). Es werden keine
            individuellen Ladevorgänge oder Nutzer-IDs angezeigt; Statistiken basieren auf mindestens 10
            Datensätzen. Diese Seiten sind auch für Suchmaschinen optimiert, um Kaufinteressierten reale
            Verbrauchsdaten zu bieten.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">5. Datenquellen &amp; Attribution</h2>
          <p>Importierte Daten werden transparent gekennzeichnet:</p>
          <ul class="list-disc list-inside space-y-2 mt-2">
            <li><strong>Eigene Importe:</strong> Nutzer können Ladevorgänge aus externen Quellen importieren (z. B. Spritmonitor, Tesla, Smartcar). Die Daten gehören dem Nutzer und werden gekennzeichnet (z. B. SPRITMONITOR_IMPORT).</li>
            <li><strong>WLTP-Werte:</strong> offizielle, öffentlich verfügbare Herstellerangaben (Combined Cycle).</li>
            <li><strong>Community-Beiträge:</strong> von Nutzern manuell erfasste Ladevorgänge und ergänzte WLTP-Werte fließen anonymisiert in öffentliche Statistiken ein.</li>
          </ul>
          <p class="mt-4">
            Stammen auf einer Modellseite mehr als 80% der Daten aus Importen, wird ein entsprechender Hinweis angezeigt.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">6. Kostenpflichtige Funktionen</h2>
          <p>
            Optional sind kostenpflichtige Zusatzfunktionen verfügbar. Die Abwicklung von Zahlungen erfolgt über
            unseren Zahlungsdienstleister Stripe (siehe
            <router-link to="/datenschutz" class="text-green-600 hover:text-green-700">Datenschutzerklärung</router-link>).
            Preise und Leistungsumfang werden vor dem Kauf angezeigt. Gesetzliche Verbraucherrechte, insbesondere
            das Widerrufsrecht bei digitalen Inhalten, bleiben unberührt.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">7. Gamification &amp; Coins</h2>
          <p>
            Für bestimmte Aktionen (z. B. Community-Beiträge, Streaks) erhalten Nutzer virtuelle Coins. Coins
            haben keinen monetären Wert, sind nicht übertragbar und können nicht ausgezahlt werden. Wir behalten
            uns vor, das Coin-System jederzeit anzupassen oder einzustellen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">8. Haftung</h2>
          <ul class="list-disc list-inside space-y-2">
            <li>EV Monitor wird ohne Gewährleistung für fehlerfreie oder ununterbrochene Verfügbarkeit bereitgestellt.</li>
            <li>Für fehlerhafte Eingabedaten, Datenverlust oder mittelbare Schäden übernehmen wir keine Haftung, soweit gesetzlich zulässig. Die Haftung für Vorsatz und grobe Fahrlässigkeit sowie nach zwingenden gesetzlichen Vorschriften bleibt unberührt.</li>
            <li>Für Inhalte externer, verlinkter Websites ist der jeweilige Anbieter verantwortlich.</li>
            <li>Wir empfehlen, wichtige Daten zusätzlich extern zu sichern.</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">9. Änderungen</h2>
          <p>
            Wir behalten uns vor, diese Nutzungsbedingungen anzupassen. Wesentliche Änderungen kündigen wir per
            E-Mail oder im Dienst an. Durch fortgesetzte Nutzung nach Inkrafttreten gelten die geänderten
            Bedingungen als akzeptiert.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">10. Anbieter &amp; Kontakt</h2>
          <p>
            Anbieter ist Sebastian Wien; vollständige Angaben im
            <router-link to="/impressum" class="text-green-600 hover:text-green-700">Impressum</router-link>.<br />
            E-Mail: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>
          </p>
          <p class="mt-4"><strong>Stand:</strong> Juni 2026</p>
        </section>
      </div>

      <div class="mt-12 pt-8 border-t border-gray-200 dark:border-gray-700">
        <router-link to="/" class="text-green-600 hover:text-green-700 font-medium">
          ← Zurück zur Startseite
        </router-link>
      </div>
    </div>
  </div>
</template>
