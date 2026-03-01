<script setup lang="ts">
import { BoltIcon } from '@heroicons/vue/24/outline'
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
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      <h1 class="text-4xl font-bold text-gray-900 mb-8">Allgemeine Geschäftsbedingungen (AGB)</h1>

      <div class="prose prose-lg text-gray-600 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">1. Geltungsbereich</h2>
          <p>
            Diese Allgemeinen Geschäftsbedingungen (AGB) gelten für die Nutzung der Plattform "EV Monitor"
            (im Folgenden "Plattform" oder "Service"). Mit der Registrierung und Nutzung des Services
            erklären Sie sich mit diesen AGB einverstanden.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">2. Leistungsumfang</h2>
          <p>
            EV Monitor bietet folgende Leistungen:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Tracking von Elektroauto-Ladevorgängen</li>
            <li>Statistische Auswertungen Ihrer Daten</li>
            <li>Vergleich mit WLTP-Herstellerangaben</li>
            <li>Zugriff auf anonymisierte Community-Statistiken</li>
            <li>Optional: Automatischer Import von Tesla-Ladevorgängen</li>
          </ul>
          <p class="mt-4">
            Der Service ist derzeit <strong>kostenlos</strong>. Wir behalten uns vor, zukünftig kostenpflichtige
            Premium-Features einzuführen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">3. Registrierung und Nutzerkonto</h2>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">3.1 Registrierung</h3>
          <p>
            Zur Nutzung des Services ist eine Registrierung mit E-Mail-Adresse und Passwort erforderlich.
            Die E-Mail-Adresse muss verifiziert werden.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">3.2 Pflichten des Nutzers</h3>
          <ul class="list-disc list-inside space-y-2">
            <li>Sie müssen bei der Registrierung wahrheitsgemäße Angaben machen</li>
            <li>Sie sind verpflichtet, Ihr Passwort geheim zu halten</li>
            <li>Sie haften für alle Aktivitäten, die unter Ihrem Account durchgeführt werden</li>
            <li>Bei Verdacht auf unbefugten Zugriff informieren Sie uns umgehend</li>
          </ul>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">3.3 Account-Löschung</h3>
          <p>
            Sie können Ihren Account jederzeit löschen. Dabei werden alle personenbezogenen Daten
            unwiderruflich gelöscht. Anonymisierte Statistiken bleiben bestehen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">4. Nutzungsregeln</h2>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">4.1 Erlaubte Nutzung</h3>
          <p>
            Der Service darf ausschließlich für private, nicht-kommerzielle Zwecke genutzt werden.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">4.2 Verbotene Nutzung</h3>
          <p>Folgende Handlungen sind untersagt:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Eingabe falscher oder irreführender Daten</li>
            <li>Manipulation von Statistiken durch Fake-Daten</li>
            <li>Automatisiertes Auslesen (Scraping) der Plattform</li>
            <li>Reverse Engineering oder Dekompilierung des Codes</li>
            <li>Überlastung der Server durch DoS-Angriffe</li>
            <li>Verbreitung von Malware oder schädlichem Code</li>
            <li>Belästigung anderer Nutzer (falls Community-Features eingeführt werden)</li>
          </ul>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">4.3 Community-Daten</h3>
          <p>
            Wenn Sie Daten teilen (z.B. WLTP-Angaben), verpflichten Sie sich zu wahrheitsgemäßen Angaben.
            Bewusst falsche Daten können zur Sperrung Ihres Accounts führen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">5. Haftung und Gewährleistung</h2>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">5.1 Verfügbarkeit</h3>
          <p>
            Wir bemühen uns um hohe Verfügbarkeit des Services. Eine Garantie für ununterbrochene
            Verfügbarkeit wird nicht gegeben. Wartungsarbeiten werden nach Möglichkeit angekündigt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">5.2 Datenrichtigkeit</h3>
          <p>
            Alle Statistiken und WLTP-Vergleiche basieren auf Community-Daten und Herstellerangaben.
            Wir übernehmen keine Gewähr für die Richtigkeit oder Vollständigkeit dieser Daten.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">5.3 Haftungsbeschränkung</h3>
          <p>
            Wir haften nur für Schäden, die durch vorsätzliches oder grob fahrlässiges Verhalten entstehen.
            Die Haftung für leichte Fahrlässigkeit ist ausgeschlossen, soweit nicht Leben, Körper, Gesundheit
            oder wesentliche Vertragspflichten betroffen sind.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 mt-6 mb-3">5.4 Tesla-Integration</h3>
          <p>
            Die Tesla-Integration nutzt die offizielle Tesla Owner API. Wir übernehmen keine Haftung für:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Änderungen oder Abschaltung der Tesla API</li>
            <li>Fehlerhafte Datenübertragung</li>
            <li>Unberechtigten Zugriff aufgrund kompromittierter Tesla-Credentials</li>
          </ul>
          <p class="mt-4">
            Ihre Tesla Access Tokens werden AES-verschlüsselt gespeichert. Sie können die Verbindung
            jederzeit trennen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">6. Urheberrechte und Lizenzen</h2>
          <p>
            Alle Inhalte der Plattform (Code, Design, Texte) sind urheberrechtlich geschützt.
          </p>
          <p class="mt-4">
            <strong>Ihre Daten:</strong> Sie behalten alle Rechte an Ihren eingegebenen Daten.
            Durch die Nutzung räumen Sie uns ein nicht-exklusives Recht ein, Ihre Daten in anonymisierter
            Form für Statistiken zu verwenden.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">7. Änderungen der AGB</h2>
          <p>
            Wir behalten uns vor, diese AGB jederzeit zu ändern. Änderungen werden per E-Mail angekündigt.
            Wenn Sie den Änderungen nicht zustimmen, können Sie Ihren Account löschen.
          </p>
          <p class="mt-4">
            Die fortgesetzte Nutzung nach Änderungen gilt als Zustimmung zu den neuen AGB.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">8. Kündigung</h2>
          <p>
            Beide Parteien können das Nutzungsverhältnis jederzeit ohne Einhaltung einer Frist kündigen.
          </p>
          <p class="mt-4">
            Wir behalten uns vor, Accounts bei Verstoß gegen diese AGB zu sperren oder zu löschen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">9. Salvatorische Klausel</h2>
          <p>
            Sollten einzelne Bestimmungen dieser AGB unwirksam sein, bleibt die Wirksamkeit der übrigen
            Bestimmungen unberührt.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">10. Anwendbares Recht</h2>
          <p>
            Für diese AGB gilt deutsches Recht unter Ausschluss des UN-Kaufrechts (CISG).
          </p>
          <p class="mt-4">
            Gerichtsstand ist [Dein Ort], soweit gesetzlich zulässig.
          </p>
        </section>

        <section class="mt-8 pt-6 border-t border-gray-200">
          <p class="text-sm text-gray-500">
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
