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
      <h1 class="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-8">Datenschutzerklärung</h1>

      <div class="prose prose-lg text-gray-600 dark:text-gray-400 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">1. Verantwortlicher</h2>
          <p>
            Verantwortlich für die Datenverarbeitung ist:<br />
            Sebastian Wien<br />
            Cotheniusstraße 5, 10407 Berlin<br />
            E-Mail: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">2. Geltungsbereich</h2>
          <p>
            Diese Erklärung gilt für unsere Website (ev-monitor.net) <strong>und</strong> unsere native App
            (iOS und Android). Funktionsumfang und Datenverarbeitung sind auf allen Plattformen weitgehend
            identisch; abweichende App-Besonderheiten sind in Abschnitt 14 beschrieben.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">3. Hosting</h2>
          <p>
            Unser Dienst wird in Deutschland bei der <strong>Hetzner Online GmbH</strong>
            (Industriestr. 25, 91710 Gunzenhausen) gehostet. Hetzner verarbeitet Daten ausschließlich in unserem
            Auftrag auf Basis eines Vertrags zur Auftragsverarbeitung (Art. 28 DSGVO). Die Server stehen in
            Deutschland.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an einem
            sicheren, performanten Betrieb).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">4. Registrierung &amp; Account</h2>
          <p>Zur Nutzung ist eine Registrierung erforderlich. Dabei werden erhoben:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>E-Mail-Adresse (Login und Verifizierung)</li>
            <li>Benutzername (Anzeige im System)</li>
            <li>Passwort (mit BCrypt gehasht, nicht im Klartext gespeichert)</li>
          </ul>
          <p class="mt-4">
            Die E-Mail-Verifizierung ist verpflichtend; der Verifizierungslink ist 24 Stunden gültig.
          </p>
          <p class="mt-4">
            <strong>Anmeldung mit Google (nur Web):</strong> Auf der Website können Sie sich alternativ mit
            Ihrem Google-Konto registrieren und anmelden. Dabei übermittelt <strong>Google</strong>
            (Google Ireland Ltd., Irland, bzw. Google LLC, USA) uns Ihre E-Mail-Adresse und Ihren Namen zur
            Kontoerstellung; im Gegenzug erfährt Google, dass Sie sich bei EV Monitor anmelden. In der nativen
            App (iOS/Android) ist diese Option deaktiviert. Rechtsgrundlage ist Ihre Einwilligung
            (Art. 6 Abs. 1 lit. a DSGVO), erteilt durch Auswahl des Google-Logins; zur Übermittlung in die USA
            siehe Abschnitt 17.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Erfüllung des Nutzungsvertrags).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">5. Ladevorgänge &amp; Fahrten</h2>
          <p>Beim Erfassen von Lade- und Fahrtdaten speichern wir u. a.:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Geladene Energie (kWh), Kosten (EUR), Ladedauer</li>
            <li>Kilometerstand und Verbrauch (optional)</li>
            <li>Zeitstempel</li>
          </ul>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Kernfunktion des Dienstes).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">6. Standortdaten</h2>
          <p>
            Für die dauerhaft in Ihrem Konto sichtbaren Lade- und Fahrtdaten speichern wir <strong>keine</strong>
            exakten GPS-Koordinaten. Stattdessen verwenden wir <strong>Geohashing</strong>: private Ladevorgänge
            werden auf ~600m, öffentliche Ladesäulen auf ~150m anonymisiert. Aus diesem gespeicherten Geohash
            lässt sich Ihre exakte Position nicht rekonstruieren.
          </p>
          <p class="mt-4">
            <strong>Ausnahme technische Rohdaten:</strong> Wenn Sie eine Fahrzeug-Integration nutzen, die uns
            Standortsignale liefert (z. B. Smartcar), können in unseren technischen Rohdaten-Protokollen
            vorübergehend exakte Koordinaten enthalten sein. Diese Rohdaten dienen ausschließlich der
            Fahrt-Erkennung und Fehlerdiagnose und werden nach spätestens <strong>14 Tagen automatisch
            gelöscht</strong>; in Ihre dauerhaften Daten fließt nur der anonymisierte Geohash ein.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">7. Fahrzeug-Integrationen (optional)</h2>
          <p>
            Sie können optional Ihr Fahrzeug bzw. externe Datenquellen verbinden, um Lade- und Fahrtdaten
            automatisch zu importieren. Diese Integrationen werden nur aktiv, wenn Sie sie selbst einrichten:
          </p>
          <ul class="list-disc list-inside space-y-2 mt-2">
            <li><strong>Tesla</strong> (Tesla, Inc., USA): Zugriffs- und Refresh-Token werden AES-256-GCM-verschlüsselt gespeichert; Abruf von Fahrzeug-/Ladedaten über die Tesla-API.</li>
            <li><strong>Smartcar</strong> (Smartcar, Inc., USA): Verbindung zu Ihrem Fahrzeugkonto zum Abruf von Fahrzeugdaten.</li>
            <li><strong>VW Group</strong> (Volkswagen AG, Deutschland; Marken VW, Audi, Škoda, SEAT/Cupra): Verbindung zu Ihrem Hersteller-Konto zum Abruf von Fahrzeug-/Ladedaten.</li>
            <li><strong>Spritmonitor</strong> (fisch und fischl GmbH, Deutschland): Import Ihrer dort erfassten Verbrauchsdaten.</li>
            <li><strong>XPeng-Datenexport</strong>: Import von Fahrzeugdaten, die Sie über den Hersteller anfordern.</li>
          </ul>
          <p class="mt-4">
            Sie können jede Verbindung jederzeit in den Einstellungen trennen. Zur Übermittlung in Drittländer
            (insb. USA) siehe Abschnitt 17.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (von Ihnen angeforderte Funktion).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">8. Zahlungen (Stripe)</h2>
          <p>
            Für kostenpflichtige Funktionen nutzen wir den Zahlungsdienstleister <strong>Stripe</strong>
            (Stripe Payments Europe, Ltd., Irland; ggf. Stripe, Inc., USA). Bei einem Kauf werden die zur
            Zahlungsabwicklung nötigen Daten (z. B. Zahlungsmittel, Beträge, Transaktions-IDs) direkt von Stripe
            verarbeitet. Vollständige Zahlungsdaten (z. B. Kartennummern) erhalten wir nicht.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Vertragsabwicklung). Datenschutz bei Stripe:
            <a href="https://stripe.com/de/privacy" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">stripe.com/de/privacy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">9. Zweck der Datenverarbeitung</h2>
          <p>Die erhobenen Daten werden verwendet für:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Bereitstellung und Verbesserung des Dienstes</li>
            <li>Anzeige persönlicher Statistiken (nur für Sie sichtbar)</li>
            <li>Berechnung anonymisierter Community-Statistiken</li>
            <li>WLTP-Vergleiche (nur aggregierte Daten)</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">10. Community-Daten &amp; Anonymisierung</h2>
          <p>Ihre Ladevorgänge können in <strong>anonymisierten</strong> Community-Statistiken einfließen:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Durchschnittliche Kosten pro kWh für ein Fahrzeugmodell</li>
            <li>Durchschnittlicher Verbrauch pro 100 km</li>
            <li>Anzahl der Ladevorgänge pro Modell</li>
          </ul>
          <p class="mt-4">
            In öffentlichen Statistiken werden <strong>keine</strong> persönlichen Daten oder Nutzernamen
            angezeigt; alle Werte sind aggregiert und anonymisiert.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an
            aussagekräftigen, anonymen Vergleichswerten).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">11. Cookies &amp; lokale Speicherung</h2>
          <p>
            Beim Seitenaufruf setzen wir <strong>keine Tracking-Cookies</strong>. Technisch notwendig ist
            lediglich:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Session-Token (JWT):</strong> im lokalen Speicher (localStorage) zur Authentifizierung, bis zu 30 Tage gültig.</li>
          </ul>
          <p class="mt-4">
            Diese Speicherung ist für den von Ihnen gewünschten Login unbedingt erforderlich (§ 25 Abs. 2 TTDSG)
            und bedarf keiner Einwilligung. Tracking-Dienste, die eine Einwilligung erfordern (Reddit-Pixel,
            siehe Abschnitt 13), werden erst nach Ihrer Zustimmung aktiv.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">12. Webanalyse (Plausible)</h2>
          <p>
            Wir nutzen <strong>Plausible Analytics</strong> (Plausible Insights OÜ, Tartu, Estland - EU), einen
            datenschutzfreundlichen Webanalysedienst <strong>ohne Cookies</strong> und ohne Erhebung
            personenbezogener Daten. Kein Fingerprinting, kein geräteübergreifendes Tracking.
          </p>
          <p class="mt-4">Erfasst werden anonymisiert u. a.: aufgerufene Seiten, Herkunft des Besuchs, Land (aus der IP abgeleitet, die IP selbst wird nicht gespeichert), Gerätetyp und anonyme Nutzungsaktionen.</p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO. Da keine personenbezogenen Daten
            erhoben werden, ist kein Einwilligungsbanner erforderlich. Infos:
            <a href="https://plausible.io/data-policy" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">plausible.io/data-policy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">13. Reddit-Pixel (nur mit Einwilligung)</h2>
          <p>
            Wenn Sie über eine Reddit-Anzeige zu uns gelangen, fragen wir Ihre Einwilligung für den
            <strong>Reddit-Pixel</strong> (Reddit, Inc., USA) ab. Der Pixel wird <strong>nur</strong> geladen,
            wenn Sie aktiv zustimmen, und misst dann den Erfolg unserer Werbung (z. B. ob eine Registrierung auf
            eine Anzeige zurückgeht). Ohne Zustimmung wird kein Reddit-Code geladen.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. a DSGVO (Einwilligung), jederzeit mit Wirkung
            für die Zukunft widerrufbar. Zur Übermittlung in die USA siehe Abschnitt 17. Datenschutz bei Reddit:
            <a href="https://www.reddit.com/policies/privacy-policy" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">reddit.com/policies/privacy-policy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">14. Besonderheiten der App</h2>
          <p>
            Bei Installation und Nutzung der iOS-App verarbeitet <strong>Apple</strong> (Apple Inc., USA) im
            Rahmen des App Store eigenverantwortlich Daten (z. B. Download, optionale Diagnose- und
            Absturzberichte). Hierauf haben wir keinen Einfluss; es gilt die Datenschutzerklärung von Apple.
          </p>
          <p class="mt-4">
            Bei Installation und Nutzung der Android-App verarbeitet entsprechend <strong>Google</strong>
            (Google Ireland Ltd., Irland, bzw. Google LLC, USA) im Rahmen von Google Play eigenverantwortlich
            Daten (z. B. Download, optionale Absturz- und Nutzungsberichte). Hierauf haben wir keinen Einfluss;
            es gilt die Datenschutzerklärung von Google.
          </p>
          <p class="mt-4">
            Die App lädt Funktions-Updates als Web-Pakete von <strong>unserem eigenen Server</strong> (kein
            Drittanbieter-Dienst, keine Übermittlung von Gerätedaten an Dritte zu diesem Zweck).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">15. Affiliate-Links (Awin)</h2>
          <p>
            Wir nehmen am Affiliate-Programm von <strong>Awin</strong> (Awin AG, Eichhornstraße 3, 10785 Berlin)
            teil. Als "Anzeige" gekennzeichnete Links leiten beim Klick über den Awin-Server weiter; dabei kann
            Awin ein Cookie (Laufzeit bis 24 Stunden) setzen, um eine Vermittlung nachzuvollziehen. Es werden
            keine personenbezogenen Daten von uns an Awin übertragen.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (Finanzierung des kostenfreien
            Angebots). Infos:
            <a href="https://www.awin.com/de/rechtliches/datenschutzrichtlinie" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">awin.com/de/rechtliches/datenschutzrichtlinie</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">16. Unterstützung: Ko-fi &amp; PayPal</h2>
          <p>
            Wir verlinken auf Ko-fi (<a href="https://ko-fi.com" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">ko-fi.com</a>)
            und PayPal (<a href="https://www.paypal.com" target="_blank" rel="noopener noreferrer" class="text-green-600 hover:text-green-700">paypal.com</a>),
            über die Sie die Entwicklung freiwillig unterstützen können. Wir betten keinen Code dieser Dienste
            ein; erst beim Besuch der jeweiligen Seite gelten deren Datenschutzbestimmungen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">17. Weitergabe &amp; Übermittlung in Drittländer</h2>
          <p>Wir geben Ihre Daten <strong>nicht</strong> an Dritte weiter, außer:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>an die in dieser Erklärung genannten Auftragsverarbeiter/Dienste im beschriebenen Umfang,</li>
            <li>bei gesetzlicher Verpflichtung,</li>
            <li>wenn Sie ausdrücklich eingewilligt haben.</li>
          </ul>
          <p class="mt-4">
            Bei Nutzung bestimmter Dienste (Tesla, Smartcar, Stripe, Reddit, Apple, Google) können Daten in die
            <strong>USA</strong> übermittelt werden. Soweit kein Angemessenheitsbeschluss greift, stützt sich die
            Übermittlung auf geeignete Garantien (EU-Standardvertragsklauseln gem. Art. 46 DSGVO) bzw. - bei
            einwilligungsbasierten Diensten - auf Ihre Einwilligung (Art. 49 Abs. 1 lit. a DSGVO). In den USA
            besteht ggf. kein mit der EU vergleichbares Datenschutzniveau (z. B. Zugriff durch Behörden).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">18. Ihre Rechte</h2>
          <p>Nach der DSGVO haben Sie das Recht auf:</p>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Auskunft</strong> über die zu Ihnen gespeicherten Daten (Art. 15)</li>
            <li><strong>Berichtigung</strong> unrichtiger Daten (Art. 16)</li>
            <li><strong>Löschung</strong> Ihres Accounts und aller zugehörigen Daten (Art. 17)</li>
            <li><strong>Einschränkung</strong> der Verarbeitung (Art. 18)</li>
            <li><strong>Datenübertragbarkeit</strong> - Export Ihrer Daten im JSON-Format (Art. 20)</li>
            <li><strong>Widerspruch</strong> gegen die Verarbeitung (Art. 21)</li>
            <li><strong>Widerruf</strong> erteilter Einwilligungen mit Wirkung für die Zukunft (Art. 7 Abs. 3)</li>
          </ul>
          <p class="mt-4">
            Zur Ausübung genügt eine Nachricht an
            <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>.
          </p>
          <p class="mt-4">
            <strong>Beschwerderecht:</strong> Sie können sich bei einer Datenschutz-Aufsichtsbehörde beschweren
            (Art. 77 DSGVO), insbesondere in dem EU-Land Ihres Aufenthaltsorts. Für uns zuständig ist die
            Berliner Beauftragte für Datenschutz und Informationsfreiheit.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">19. Datensicherheit</h2>
          <ul class="list-disc list-inside space-y-2">
            <li>HTTPS/TLS-Verschlüsselung für alle Verbindungen</li>
            <li>BCrypt-Hashing für Passwörter</li>
            <li>AES-256-GCM-Verschlüsselung für sensible Tokens</li>
            <li>JWT mit Signatur-Validierung</li>
            <li>Regelmäßige Security-Updates</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">20. Speicherdauer</h2>
          <p>
            Account- und Nutzungsdaten speichern wir, solange Ihr Account besteht. Bei Account-Löschung werden
            alle personenbezogenen Daten unwiderruflich gelöscht; bereits anonymisierte, nicht mehr auf Sie
            beziehbare Statistikwerte bleiben bestehen. Daten, für die gesetzliche Aufbewahrungsfristen gelten
            (z. B. Rechnungsdaten bei Käufen), werden bis zum Ablauf dieser Fristen aufbewahrt und dann gelöscht.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">21. Änderungen dieser Erklärung</h2>
          <p>
            Wir passen diese Datenschutzerklärung bei geänderter Rechtslage oder Änderungen des Dienstes an.
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
