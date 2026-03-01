<script setup lang="ts">
// Terms of Service & Privacy Policy
</script>

<template>
  <div class="min-h-screen bg-gray-50 py-12 px-4">
    <div class="max-w-4xl mx-auto bg-white rounded-xl shadow-lg p-8">
      <h1 class="text-4xl font-bold text-gray-900 mb-8">
        Allgemeine Geschäftsbedingungen & Datenschutz
      </h1>

      <!-- AGB -->
      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          1. Allgemeines
        </h2>
        <p class="text-gray-700 mb-4 leading-relaxed">
          EV Monitor ist eine kostenlose Web-Anwendung zum Tracken von Elektroauto-Ladevorgängen.
          Die Nutzung erfolgt auf freiwilliger Basis. Wir behalten uns vor, den Service jederzeit
          zu ändern oder einzustellen.
        </p>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          2. Registrierung & Nutzerverantwortung
        </h2>
        <div class="text-gray-700 space-y-3 leading-relaxed">
          <p>
            <strong>2.1 Account-Erstellung:</strong> Für die Nutzung ist eine Registrierung mit
            E-Mail-Adresse und Passwort erforderlich. Die E-Mail-Adresse muss verifiziert werden.
          </p>
          <p>
            <strong>2.2 Datensorgfalt:</strong> Nutzer sind selbst verantwortlich für die
            Korrektheit ihrer eingegebenen Daten (kWh, Kosten, Standort, Fahrzeugmodell).
          </p>
          <p>
            <strong>2.3 Keine Garantie:</strong> EV Monitor bietet keine Gewähr für die Richtigkeit
            von Verbrauchsstatistiken oder WLTP-Vergleichen. Alle Daten dienen nur zu
            Informationszwecken.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          3. Datenschutz (DSGVO)
        </h2>
        <div class="text-gray-700 space-y-3 leading-relaxed">
          <p>
            <strong>3.1 Gespeicherte Daten:</strong>
          </p>
          <ul class="list-disc ml-6 space-y-2">
            <li>E-Mail-Adresse (für Login & Verifizierung)</li>
            <li>Username (öffentlich sichtbar in Rankings/Leaderboards)</li>
            <li>Passwort (BCrypt-Hash, nicht reversibel)</li>
            <li>Ladevorgänge (kWh, Kosten, Datum, Geohash, Fahrzeug-ID)</li>
            <li>Fahrzeuge (Marke, Modell, Kennzeichen, Batteriekapazität)</li>
            <li>WLTP-Beiträge (crowdsourced Reichweiten-/Verbrauchsdaten)</li>
          </ul>

          <p class="mt-4">
            <strong>3.2 Privacy-First Geohashing:</strong>
            GPS-Koordinaten werden NIEMALS gespeichert. Stattdessen wird ein 5-Zeichen-Geohash
            (~5km Präzision) berechnet und gespeichert. Beispiel: <code class="bg-gray-100 px-2 py-1 rounded">u33d1</code>
            = ca. 5km Radius um Berlin Mitte.
          </p>

          <p>
            <strong>3.3 Datenübermittlung:</strong>
            Alle Daten werden verschlüsselt übertragen (HTTPS/TLS). Es erfolgt keine Weitergabe an
            Dritte, außer für technische Dienstleister (z.B. Hosting).
          </p>

          <p>
            <strong>3.4 Löschung:</strong>
            Nutzer können jederzeit ihr Konto und alle damit verbundenen Daten löschen (Funktion in
            Entwicklung). Bei Inaktivität >24 Monate behalten wir uns vor, Accounts zu löschen.
          </p>

          <p>
            <strong>3.5 Cookies & Tracking:</strong>
            Wir verwenden nur technisch notwendige Cookies (JWT-Token für Session-Management).
            Keine Tracking-Cookies, kein Google Analytics.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          4. Public Model Pages (Öffentliche Statistiken)
        </h2>
        <div class="text-gray-700 space-y-3 leading-relaxed">
          <p>
            <strong>4.1 Aggregierte Daten:</strong>
            Für jedes Fahrzeugmodell werden anonymisierte, aggregierte Statistiken öffentlich
            angezeigt (z.B. durchschnittlicher Verbrauch, Kosten pro kWh, Anzahl Ladevorgänge).
          </p>
          <p>
            <strong>4.2 Keine Personenbezüge:</strong>
            Es werden KEINE individuellen Ladevorgänge oder Nutzer-IDs angezeigt. Alle Statistiken
            basieren auf mindestens 10 Datensätzen (Privacy Threshold).
          </p>
          <p>
            <strong>4.3 SEO-Zweck:</strong>
            Diese Seiten sind für Suchmaschinen optimiert, um unentschlossenen EV-Käufern
            Verbrauchsdaten zu bieten.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          5. Datenquellen & Attribution
        </h2>
        <div class="text-gray-700 space-y-4 leading-relaxed">
          <p>
            EV Monitor ermöglicht den Import von Ladevorgängen aus folgenden Quellen. Alle
            importierten Daten werden transparent gekennzeichnet:
          </p>

          <div class="bg-green-50 border-l-4 border-green-500 p-4 rounded">
            <p class="font-semibold text-green-900 mb-2">
              5.1 Sprit-Monitor API
            </p>
            <ul class="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                Nutzer können eigene Ladevorgänge per API-Token importieren
              </li>
              <li>
                <strong>Datenbesitz:</strong> Alle importierten Daten gehören dem Nutzer und
                werden nicht an Sprit-Monitor zurückgesendet
              </li>
              <li>
                <strong>Privacy-Schutz:</strong> GPS-Koordinaten werden zu 5km-Geohashes
                konvertiert (siehe 3.2)
              </li>
              <li>
                <strong>Attribution:</strong> Importierte Logs werden als
                <code class="bg-gray-100 px-2 py-1 rounded text-sm">SPRITMONITOR_IMPORT</code>
                gekennzeichnet
              </li>
              <li>
                <strong>API powered by:</strong>
                <a
                  href="https://www.spritmonitor.de"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-green-600 hover:underline">
                  spritmonitor.de
                </a>
              </li>
            </ul>
          </div>

          <div class="bg-blue-50 border-l-4 border-blue-500 p-4 rounded">
            <p class="font-semibold text-blue-900 mb-2">
              5.2 WLTP-Daten (Herstellerangaben)
            </p>
            <ul class="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                Offizielle WLTP-Werte (Combined Cycle) von Fahrzeugherstellern
              </li>
              <li>
                Öffentlich verfügbare Daten, keine Copyright-Verletzung
              </li>
              <li>
                Crowdsourced Ergänzungen durch die Community (siehe 5.3)
              </li>
            </ul>
          </div>

          <div class="bg-purple-50 border-l-4 border-purple-500 p-4 rounded">
            <p class="font-semibold text-purple-900 mb-2">
              5.3 Community-Beiträge (Crowdsourcing)
            </p>
            <ul class="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                Manuell eingetragene Ladevorgänge von registrierten Nutzern
                (<code class="bg-gray-100 px-2 py-1 rounded text-sm">USER_LOGGED</code>)
              </li>
              <li>
                WLTP-Werte, die von Nutzern für fehlende Modelle beigesteuert werden
              </li>
              <li>
                Aggregierte Statistiken werden anonymisiert auf Public Model Pages angezeigt
              </li>
              <li>
                Belohnung: 50 Coins pro neuem WLTP-Datensatz (Gamification)
              </li>
            </ul>
          </div>

          <p class="mt-4 text-sm text-gray-600 italic">
            <strong>Transparenz-Hinweis:</strong> Wenn auf Public Model Pages mehr als 80% der
            Daten aus Imports stammen, wird ein entsprechender Disclaimer angezeigt.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          6. Gamification & Coins
        </h2>
        <div class="text-gray-700 space-y-3 leading-relaxed">
          <p>
            <strong>6.1 Coin-System:</strong>
            Nutzer erhalten virtuelle Coins für bestimmte Aktionen (z.B. WLTP-Beiträge,
            Ladevorgänge, Streaks). Coins haben keinen monetären Wert und können nicht ausgezahlt
            werden.
          </p>
          <p>
            <strong>6.2 Änderungen vorbehalten:</strong>
            Wir behalten uns vor, das Coin-System jederzeit anzupassen oder einzustellen.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          7. Haftungsausschluss
        </h2>
        <div class="text-gray-700 space-y-3 leading-relaxed">
          <p>
            <strong>7.1 Keine Gewährleistung:</strong>
            EV Monitor wird "as is" bereitgestellt. Wir übernehmen keine Haftung für fehlerhafte
            Daten, Datenverlust oder Schäden durch die Nutzung der Plattform.
          </p>
          <p>
            <strong>7.2 Externe Links:</strong>
            Wir haften nicht für Inhalte externer Websites (z.B. Sprit-Monitor, OpenStreetMap).
          </p>
          <p>
            <strong>7.3 Beta-Status:</strong>
            Die Plattform befindet sich in der Beta-Phase. Es können Bugs auftreten. Wir empfehlen,
            wichtige Daten zusätzlich extern zu sichern.
          </p>
        </div>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          8. Änderungen der AGB
        </h2>
        <p class="text-gray-700 leading-relaxed">
          Wir behalten uns vor, diese AGB jederzeit zu ändern. Änderungen werden per E-Mail
          angekündigt. Durch weitere Nutzung nach Änderung akzeptieren Nutzer die neuen AGB.
        </p>
      </section>

      <section class="mb-12">
        <h2 class="text-2xl font-bold text-gray-800 mb-4 border-b-2 border-green-500 pb-2">
          9. Kontakt & Impressum
        </h2>
        <div class="text-gray-700 leading-relaxed">
          <p class="mb-2">
            <strong>Betreiber:</strong> EV Monitor
          </p>
          <p class="mb-2">
            <strong>E-Mail:</strong> support@ev-monitor.net (Placeholder)
          </p>
          <p class="text-sm text-gray-600 mt-4">
            <strong>Stand:</strong> 1. März 2026
          </p>
        </div>
      </section>

      <!-- Back Button -->
      <div class="mt-12 text-center">
        <router-link
          to="/"
          class="inline-block px-6 py-3 bg-green-600 hover:bg-green-700 text-white rounded-lg
                 font-medium shadow-md transition">
          ← Zurück zur Startseite
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
code {
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}
</style>
