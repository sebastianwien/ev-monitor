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
            Verantwortlich für die Datenverarbeitung ist:<br>
            Sebastian Wien<br>
            Cotheniusstraße 5, 10407 Berlin<br>
            E-Mail: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>
          </p>
          <p class="mt-4">Ein Datenschutzbeauftragter ist nicht bestellt, da keine gesetzliche Pflicht besteht.</p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">2. Geltungsbereich</h2>
          <p>
            Diese Erklärung gilt für unsere Website (ev-monitor.net) und unsere native App (iOS und Android).
            Funktionsumfang und Datenverarbeitung sind auf allen Plattformen weitgehend identisch; abweichende
            App-Besonderheiten sind in Abschnitt 16 beschrieben.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">3. Hosting und Server-Logfiles</h2>
          <p>
            Unser Dienst wird in Deutschland bei der <strong>Hetzner Online GmbH</strong> (Industriestr. 25,
            91710 Gunzenhausen) gehostet. Hetzner verarbeitet Daten ausschließlich in unserem Auftrag auf Basis
            eines Vertrags zur Auftragsverarbeitung (Art. 28 DSGVO).
          </p>
          <p class="mt-4">
            Bei jedem Aufruf verarbeitet unser Webserver technisch notwendige Zugriffsdaten: Datum und
            Uhrzeit, aufgerufene Adresse, übertragene Datenmenge, Browser- und Betriebssystemangaben sowie die
            verweisende Seite. Die IP-Adresse wird dabei <strong>vor dem Schreiben ins Logfile gekürzt</strong>
            (IPv4 um das letzte Oktett, IPv6 auf die ersten 32 Bit), sodass die Logfiles keinen Personenbezug
            mehr haben. Sie dienen der Sicherheit, Fehleranalyse und Reichweitenauswertung und werden rollierend
            überschrieben.
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
            <li>E-Mail-Adresse (Login, Verifizierung und Benachrichtigungen zum Konto)</li>
            <li>Benutzername (Anzeige im System)</li>
            <li>Passwort (mit BCrypt gehasht, nicht im Klartext gespeichert)</li>
          </ul>
          <p class="mt-4">
            Die E-Mail-Verifizierung ist verpflichtend; der Verifizierungslink ist 24 Stunden gültig.
          </p>
          <p class="mt-4">
            <strong>Anmeldung mit Google (nur Web):</strong> Auf der Website können Sie sich alternativ mit
            Ihrem Google-Konto registrieren und anmelden. Dabei übermittelt Google (Google Ireland Ltd., Irland,
            bzw. Google LLC, USA) uns Ihre E-Mail-Adresse und Ihren Namen zur Kontoerstellung; im Gegenzug erfährt
            Google, dass Sie sich bei EV Monitor anmelden. In der nativen App ist diese Option deaktiviert. Zur
            Übermittlung in die USA siehe Abschnitt 19.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Erfüllung des Nutzungsvertrags). Die
            Bereitstellung der E-Mail-Adresse ist für den Vertragsschluss erforderlich; ohne sie kann kein Konto
            angelegt werden.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">5. Ladevorgänge, Fahrten &amp; Fahrzeugdaten</h2>
          <p>Beim Erfassen von Lade- und Fahrtdaten speichern wir u. a.:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Geladene Energie (kWh), Kosten (EUR), Ladedauer, Ladeleistung und Ladezustand (SoC)</li>
            <li>Kilometerstand, Verbrauch und Fahrtstrecke (optional bzw. bei Integrationen automatisch)</li>
            <li>Fahrzeugmodell, Batteriekapazität und optional Batteriezustand (SoH)</li>
            <li>Zeitstempel sowie die Außentemperatur zum Ladezeitpunkt (siehe Abschnitt 8)</li>
            <li>Optional: Ladekarte/Tarif und Ladeanbieter</li>
          </ul>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Kernfunktion des Dienstes).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">6. Standortdaten</h2>
          <p>
            Für die dauerhaft in Ihrem Konto sichtbaren Lade- und Fahrtdaten speichern wir <strong>keine
            exakten GPS-Koordinaten</strong>. Stattdessen verwenden wir Geohashing: private Ladeorte werden auf
            ca. 600 m, öffentliche Ladesäulen auf ca. 150 m und Start-/Endpunkte von Fahrten für die
            Kartendarstellung auf ca. 40 m vergröbert. Aus dem gespeicherten Geohash lässt sich Ihre exakte
            Position nicht rekonstruieren.
          </p>
          <p class="mt-4">
            <strong>Technische Rohdaten:</strong> Wenn Sie eine Fahrzeug-Integration nutzen, die uns
            Standortsignale liefert (z. B. Tesla, Smartcar), können in unseren technischen Rohdaten-Protokollen
            vorübergehend exakte Koordinaten enthalten sein. Diese Rohdaten dienen ausschließlich der
            Fahrt- und Ladeerkennung sowie der Fehlerdiagnose und werden nach spätestens 28 Tagen automatisch
            gelöscht; in Ihre dauerhaften Daten fließt nur der vergröberte Geohash ein.
          </p>
          <p class="mt-4">
            <strong>Standortabfrage im Browser/App:</strong> Beim Erfassen eines Ladevorgangs können Sie
            optional Ihre aktuelle Position freigeben, um Ladesäulen in der Nähe vorzuschlagen. Die Abfrage
            erfolgt nur nach Ihrer Freigabe im Browser bzw. Betriebssystem; die Position wird zur Suche an
            unseren Server übermittelt und nicht dauerhaft gespeichert.
          </p>
          <p class="mt-4">
            <strong>Ortssuche (OpenStreetMap Nominatim):</strong> Wenn Sie in einem Ortsfeld eine Adresse
            eintippen, wird der eingegebene Suchtext direkt von Ihrem Gerät an den Geokodierungsdienst Nominatim
            der OpenStreetMap Foundation (Vereinigtes Königreich) gesendet. Dabei erhält die OpenStreetMap
            Foundation Ihre IP-Adresse und den Suchtext. Für das Vereinigte Königreich besteht ein
            Angemessenheitsbeschluss der EU-Kommission. Datenschutz:
            <a href="https://osmfoundation.org/wiki/Privacy_Policy" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">osmfoundation.org/wiki/Privacy_Policy</a>.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (von Ihnen angeforderte Funktion).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">7. Fahrzeug-Integrationen (optional)</h2>
          <p>
            Sie können optional Ihr Fahrzeug bzw. externe Datenquellen verbinden, um Lade- und Fahrtdaten
            automatisch zu importieren. Diese Integrationen werden nur aktiv, wenn Sie sie selbst einrichten:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li><strong>Tesla</strong> (Tesla, Inc., USA): Zugriffs- und Refresh-Token werden AES-256-GCM-verschlüsselt gespeichert; Abruf von Fahrzeug-, Lade- und Standortdaten über die Tesla Fleet API (EU-Endpunkt).</li>
            <li><strong>Smartcar</strong> (Smartcar, Inc., USA): Verbindung zu Ihrem Fahrzeugkonto zum Abruf von Fahrzeug-, Lade- und Standortdaten.</li>
            <li><strong>VW EU-Data-Act-Portal</strong> (Volkswagen Group Info Services AG, Wolfsburg; Marken VW, Škoda, Audi, SEAT, CUPRA): Auf Ihren Wunsch melden wir uns einmalig mit Ihrer Marken-ID am Portal an und rufen danach alle 15 Minuten Ihre Ladedaten ab. Ihr Passwort wird dabei nur für diese eine Anmeldung übertragen und nicht gespeichert. Gespeichert werden ausschließlich die Anmeldesitzungs-Cookies Ihrer VW-ID (AES-256-GCM-verschlüsselt), mit denen die Portal-Sitzung ohne Passwort erneuert wird, sowie die Fahrzeug-Identifikationsnummer und die E-Mail-Adresse des Kontos. Wir legen in Ihrem Portal-Konto eine Datenanfrage an (Laufzeit ein Monat, automatisch verlängert), die Sie dort jederzeit einsehen können. Die vom Portal gelieferten Datensätze speichern wir zusätzlich unverändert für 30 Tage, um Fehler nachvollziehen und den Abgleich weiterentwickeln zu können (die Datensätze können neben Ladedaten auch die Fahrzeugposition enthalten); danach werden sie automatisch gelöscht. Beim Trennen der Verbindung löschen wir die gespeicherte Sitzung und diese Datensätze sofort und beenden die Datenanfrage; unabhängig davon können Sie die Sitzung jederzeit durch eine Passwortänderung bei VW ungültig machen. Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung); die Datenübermittlung erfolgt innerhalb der EU.</li>
            <li><strong>VW Group</strong> (Volkswagen AG, Deutschland; Marken VW, Audi, Škoda, SEAT/Cupra): Verbindung zu Ihrem Hersteller-Konto zum Abruf von Fahrzeug-/Ladedaten.</li>
            <li><strong>XPeng</strong> (XPeng-Datenexport bzw. XPeng-Konto): Import von Fahrzeugdaten, die Sie über den Hersteller anfordern oder freigeben.</li>
            <li><strong>Spritmonitor</strong> (fisch und fischl GmbH, Deutschland): Import Ihrer dort erfassten Verbrauchsdaten.</li>
            <li><strong>go-e Wallbox</strong> (go-e GmbH, Feldkirchen in Kärnten, Österreich): Abruf von Ladevorgängen Ihrer Wallbox über die go-e Cloud-API mit Ihrem API-Schlüssel.</li>
          </ul>
          <p class="mt-4">
            Sie können jede Verbindung jederzeit in den Einstellungen trennen; gespeicherte Zugangstoken werden
            dann gelöscht. Zur Übermittlung in Drittländer (insb. USA) siehe Abschnitt 19.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (von Ihnen angeforderte Funktion).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">8. Wetter- und Kartendienste (serverseitig)</h2>
          <p>
            Zur Berechnung temperaturabhängiger Verbrauchswerte fragt unser Server die Außentemperatur zum
            Ladezeitpunkt bei <strong>Open-Meteo</strong> (OpenMeteo GmbH, Bürglen, Schweiz) ab. Übermittelt werden dabei nur
            der vergröberte Standort (Geohash) und der Zeitpunkt, keine Kontodaten und nicht Ihre IP-Adresse.
            Für die Berechnung von Fahrtstrecken nutzt unser Server in gleicher Weise
            <strong>openrouteservice</strong> (HeiGIT gGmbH, Heidelberg, Deutschland).
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Bestandteil der Verbrauchsauswertung).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">9. Zahlungen (Stripe)</h2>
          <p>
            Für kostenpflichtige Funktionen nutzen wir den Zahlungsdienstleister <strong>Stripe</strong>
            (Stripe Payments Europe, Ltd., Irland; ggf. Stripe, Inc., USA). Bei einem Kauf werden die zur
            Zahlungsabwicklung nötigen Daten (z. B. Zahlungsmittel, Beträge, Transaktions-IDs) direkt von Stripe
            verarbeitet. Vollständige Zahlungsdaten (z. B. Kartennummern) erhalten wir nicht. Wir speichern
            Ihre Stripe-Kundennummer, den Abo-Status und die Laufzeit, um Ihnen die gebuchten Funktionen
            freizuschalten.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Vertragsabwicklung) sowie Art. 6 Abs. 1
            lit. c DSGVO für die Aufbewahrung von Rechnungsdaten. Datenschutz bei Stripe:
            <a href="https://stripe.com/de/privacy" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">stripe.com/de/privacy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">10. E-Mails</h2>
          <p>
            Wir senden Ihnen Transaktions-E-Mails, die für den Betrieb Ihres Kontos erforderlich sind
            (Verifizierung, Passwort-Zurücksetzen, Hinweise zu Abonnement und AGB-Änderungen). Werbliche
            Newsletter versenden wir nicht. Der Versand erfolgt über <strong>Brevo</strong> (Sendinblue SAS,
            7 rue de Madrid, 75008 Paris, Frankreich) als Auftragsverarbeiter (Art. 28 DSGVO); Brevo erhält dazu
            Ihre E-Mail-Adresse und den Inhalt der jeweiligen Nachricht.
          </p>
          <p class="mt-4">
            Wenn Sie uns per E-Mail kontaktieren, speichern wir Ihre Nachricht und Ihre Adresse zur Bearbeitung
            der Anfrage und löschen sie, sobald die Anfrage abgeschlossen ist und keine Aufbewahrungspflicht
            entgegensteht.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. b DSGVO (Vertragsdurchführung) bzw. lit. f
            (Beantwortung von Anfragen).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">11. Community-Daten, Anonymisierung &amp; geteilte Inhalte</h2>
          <p>Ihre Ladevorgänge können in anonymisierten Community-Statistiken einfließen:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Durchschnittliche Kosten pro kWh für ein Fahrzeugmodell</li>
            <li>Durchschnittlicher Verbrauch pro 100 km, auch saisonal</li>
            <li>Ladeleistung und Ladekurven pro Modell</li>
            <li>Anzahl der Ladevorgänge pro Modell</li>
          </ul>
          <p class="mt-4">
            In öffentlichen Statistiken werden <strong>keine persönlichen Daten oder Nutzernamen</strong>
            angezeigt; alle Werte sind aggregiert.
          </p>
          <p class="mt-4">
            <strong>Geteilte Ladekurven:</strong> Sie können einzelne Ladekurven über einen öffentlichen Link
            teilen. Der Link zeigt die Kurve, das Fahrzeugmodell und den Zeitraum, aber nicht Ihren Nutzernamen
            oder Standort. Sie können die Freigabe jederzeit widerrufen; der Link ist dann nicht mehr abrufbar.
          </p>
          <p class="mt-4">
            <strong>Optionale Umfragen:</strong> Gelegentlich blenden wir kurze Umfragen ein. Die Teilnahme ist
            freiwillig; Antworten speichern wir mit Bezug zu Ihrem Konto, um Mehrfachbefragungen zu vermeiden.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an
            aussagekräftigen, anonymen Vergleichswerten); für geteilte Ladekurven und Umfragen Art. 6 Abs. 1
            lit. a DSGVO (Einwilligung durch Ihre aktive Freigabe bzw. Teilnahme).
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">12. Cookies &amp; lokale Speicherung</h2>
          <p>
            Beim Seitenaufruf setzen wir <strong>keine Tracking-Cookies</strong>. Technisch notwendig ist
            lediglich die lokale Speicherung (localStorage) folgender Werte auf Ihrem Gerät:
          </p>
          <ul class="list-disc list-inside space-y-2">
            <li>Session-Token (JWT) zur Authentifizierung, bis zu 30 Tage gültig</li>
            <li>Einstellungen wie Sprache, Darstellung und angezeigte Hinweise</li>
            <li>Ihre Entscheidung zum Reddit-Pixel (Abschnitt 14)</li>
          </ul>
          <p class="mt-4">
            Diese Speicherung ist für den von Ihnen gewünschten Dienst unbedingt erforderlich (§ 25 Abs. 2 Nr. 2
            TDDDG) und bedarf keiner Einwilligung. Dienste, die eine Einwilligung erfordern, werden erst nach
            Ihrer Zustimmung aktiv.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">13. Webanalyse (Plausible)</h2>
          <p>
            Wir nutzen <strong>Plausible Analytics</strong> (Plausible Insights OÜ, Tartu, Estland - EU), einen
            datenschutzfreundlichen Webanalysedienst ohne Cookies. Erfasst werden u. a. aufgerufene Seiten,
            Herkunft des Besuchs, Land, Gerätetyp und anonyme Nutzungsaktionen. Die IP-Adresse wird bei der
            Übertragung verarbeitet, aber nicht gespeichert; es findet kein Fingerprinting und kein
            geräteübergreifendes Tracking statt.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an einer
            Reichweitenmessung ohne Nutzerprofile). Infos:
            <a href="https://plausible.io/data-policy" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">plausible.io/data-policy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">14. Reddit-Pixel (nur mit Einwilligung)</h2>
          <p>
            Wenn Sie über eine Reddit-Anzeige zu uns gelangen, fragen wir Ihre Einwilligung für den
            <strong>Reddit-Pixel</strong> (Reddit, Inc., USA) ab. Der Pixel wird nur geladen, wenn Sie aktiv
            zustimmen, und misst dann den Erfolg unserer Werbung (z. B. ob eine Registrierung auf eine Anzeige
            zurückgeht). Ohne Zustimmung wird kein Reddit-Code geladen.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. a DSGVO und § 25 Abs. 1 TDDDG (Einwilligung).
            Sie können die Einwilligung jederzeit mit Wirkung für die Zukunft widerrufen, indem Sie die
            Website-Daten von ev-monitor.net in Ihrem Browser löschen oder uns eine Nachricht senden. Zur
            Übermittlung in die USA siehe Abschnitt 19. Datenschutz bei Reddit:
            <a href="https://www.reddit.com/policies/privacy-policy" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">reddit.com/policies/privacy-policy</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">15. Affiliate-Links (Awin)</h2>
          <p>
            Wir nehmen am Affiliate-Programm von <strong>Awin</strong> (Awin AG, Eichhornstraße 3, 10785 Berlin)
            teil. Als "Anzeige" gekennzeichnete Links leiten beim Klick über den Awin-Server weiter; dabei kann
            Awin ein Cookie (Laufzeit bis 24 Stunden) setzen, um eine Vermittlung nachzuvollziehen. Auf unseren
            Seiten wird kein Awin-Code geladen; es werden keine personenbezogenen Daten von uns an Awin übertragen.
          </p>
          <p class="mt-4">
            <strong>Rechtsgrundlage:</strong> Art. 6 Abs. 1 lit. f DSGVO (Finanzierung des kostenfreien
            Angebots). Infos:
            <a href="https://www.awin.com/de/rechtliches/datenschutzrichtlinie" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">awin.com/de/rechtliches/datenschutzrichtlinie</a>.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">16. Besonderheiten der App</h2>
          <p>
            Bei Installation und Nutzung der <strong>iOS-App</strong> verarbeitet Apple (Apple Inc., USA) im
            Rahmen des App Store eigenverantwortlich Daten (z. B. Download, optionale Diagnose- und
            Absturzberichte). Hierauf haben wir keinen Einfluss; es gilt die Datenschutzerklärung von Apple.
          </p>
          <p class="mt-4">
            Bei Installation und Nutzung der <strong>Android-App</strong> verarbeitet entsprechend Google
            (Google Ireland Ltd., Irland, bzw. Google LLC, USA) im Rahmen von Google Play eigenverantwortlich
            Daten. Hierauf haben wir keinen Einfluss; es gilt die Datenschutzerklärung von Google.
          </p>
          <p class="mt-4">
            Die App lädt Funktions-Updates als Web-Pakete <strong>von unserem eigenen Server</strong> (kein
            Drittanbieter-Dienst, keine Übermittlung von Gerätedaten an Dritte zu diesem Zweck). Käufe sind in
            der App nicht möglich; sie erfolgen ausschließlich über die Website.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">17. Unterstützung: Ko-fi &amp; PayPal</h2>
          <p>
            Wir verlinken auf <a href="https://ko-fi.com" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">Ko-fi</a>
            und <a href="https://www.paypal.com" target="_blank" rel="noopener" class="text-green-600 hover:text-green-700">PayPal</a>,
            über die Sie die Entwicklung freiwillig unterstützen können. Wir betten keinen Code dieser Dienste
            ein; erst beim Besuch der jeweiligen Seite gelten deren Datenschutzbestimmungen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">18. Keine automatisierte Entscheidungsfindung</h2>
          <p>
            Wir treffen keine automatisierten Entscheidungen mit rechtlicher Wirkung und erstellen keine Profile
            im Sinne von Art. 22 DSGVO. Statistische Auswertungen dienen ausschließlich Ihrer Information.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">19. Weitergabe &amp; Übermittlung in Drittländer</h2>
          <p>Wir geben Ihre Daten nicht an Dritte weiter, außer:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>an die in dieser Erklärung genannten Auftragsverarbeiter und Dienste im beschriebenen Umfang,</li>
            <li>bei gesetzlicher Verpflichtung,</li>
            <li>wenn Sie ausdrücklich eingewilligt haben.</li>
          </ul>
          <p class="mt-4">
            Bei Nutzung bestimmter Dienste (Tesla, Smartcar, Stripe, Reddit, Apple, Google) können Daten in die
            <strong>USA</strong> übermittelt werden. Soweit der Empfänger nach dem EU-US Data Privacy Framework
            zertifiziert ist, stützt sich die Übermittlung auf den Angemessenheitsbeschluss der EU-Kommission.
            Andernfalls stützt sie sich auf EU-Standardvertragsklauseln (Art. 46 Abs. 2 lit. c DSGVO) bzw. bei
            einwilligungsbasierten Diensten auf Ihre Einwilligung (Art. 49 Abs. 1 lit. a DSGVO). In den USA besteht
            ggf. kein mit der EU vergleichbares Datenschutzniveau (z. B. Zugriff durch Behörden). Für die Ortssuche
            (Vereinigtes Königreich) und Open-Meteo (Schweiz) bestehen Angemessenheitsbeschlüsse.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">20. Ihre Rechte</h2>
          <p>Nach der DSGVO haben Sie das Recht auf:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Auskunft über die zu Ihnen gespeicherten Daten (Art. 15)</li>
            <li>Berichtigung unrichtiger Daten (Art. 16)</li>
            <li>Löschung Ihres Kontos und Ihrer personenbezogenen Daten (Art. 17)</li>
            <li>Einschränkung der Verarbeitung (Art. 18)</li>
            <li>Datenübertragbarkeit - Export Ihrer Daten im JSON-Format in den Einstellungen (Art. 20)</li>
            <li>Widerspruch gegen Verarbeitungen auf Grundlage von Art. 6 Abs. 1 lit. f (Art. 21)</li>
            <li>Widerruf erteilter Einwilligungen mit Wirkung für die Zukunft (Art. 7 Abs. 3)</li>
          </ul>
          <p class="mt-4">
            Zur Ausübung genügt eine Nachricht an
            <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>.
            Kontolöschung und Datenexport können Sie zudem selbst in den Einstellungen auslösen.
          </p>
          <p class="mt-4">
            <strong>Beschwerderecht:</strong> Sie können sich bei einer Datenschutz-Aufsichtsbehörde beschweren
            (Art. 77 DSGVO), insbesondere in dem EU-Land Ihres Aufenthaltsorts. Für uns zuständig ist die
            Berliner Beauftragte für Datenschutz und Informationsfreiheit.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">21. Datensicherheit</h2>
          <ul class="list-disc list-inside space-y-2">
            <li>HTTPS/TLS-Verschlüsselung für alle Verbindungen</li>
            <li>BCrypt-Hashing für Passwörter</li>
            <li>AES-256-GCM-Verschlüsselung für Zugangstoken zu Fahrzeug-Integrationen</li>
            <li>JWT mit Signatur-Validierung</li>
            <li>Regelmäßige Security-Updates und Sicherungskopien</li>
          </ul>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">22. Speicherdauer</h2>
          <p>
            Konto- und Nutzungsdaten speichern wir, solange Ihr Konto besteht. Bei Kontolöschung werden Ihre
            Kontodaten, Zugangstoken, Integrations-Rohdaten und alle Sie identifizierenden Angaben
            unwiderruflich gelöscht. Erfasste Lade- und Fahrtwerte werden anonymisiert, also vom Konto getrennt
            und ohne Nutzer-, Standort- oder Zeitbezug, der eine Zuordnung erlauben würde, weitergeführt;
            sie sind danach keine personenbezogenen Daten mehr.
          </p>
          <p class="mt-4">
            Technische Rohdaten aus Integrationen werden nach spätestens 28 Tagen gelöscht; Server-Logfiles
            enthalten nur gekürzte IP-Adressen (Abschnitt 3) und werden rollierend überschrieben. Daten, für die gesetzliche Aufbewahrungsfristen gelten (z. B.
            Rechnungsdaten bei Käufen, bis zu 10 Jahre nach § 147 AO), werden bis zum Ablauf dieser Fristen
            aufbewahrt und dann gelöscht. Sicherungskopien der Datenbank werden rotierend aufbewahrt; gelöschte
            Daten können darin für begrenzte Zeit verbleiben und werden nicht mehr in den Dienst zurückgespielt.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">23. Änderungen dieser Erklärung</h2>
          <p>
            Wir passen diese Datenschutzerklärung bei geänderter Rechtslage oder Änderungen des Dienstes an. Die
            jeweils aktuelle Fassung finden Sie unter ev-monitor.net/datenschutz.
          </p>
          <p class="mt-4 text-sm text-gray-500">Stand: September 2026</p>
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
